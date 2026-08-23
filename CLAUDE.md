# Bolsa Widgets — convenciones del proyecto

App Android **estrictamente personal** de seguimiento de inversiones. El producto real son
los **widgets de pantalla de inicio**; la app es el panel de configuración y la vista ampliada.
Sin Play Store, sin usuarios, sin backend.

---

## 1. Idioma

| Qué | Idioma |
| --- | --- |
| Código, nombres de símbolos, comentarios, KDoc, mensajes de commit | Inglés |
| Todo el texto visible en la UI y en los widgets | Español |
| Documentación del repo (este archivo, `PLAN.md`) | Español |

Los nombres del dominio se traducen al inglés en el código: `cantidad` → `quantity`,
`precioMedioCompra` → `averageBuyPrice`, `divisa` → `currency`, `fechaCompra` → `purchaseDate`,
`notas` → `notes`.

Los textos de UI van en `res/values/strings.xml`. No hay i18n: solo español.

---

## 2. Stack fijado

No se añade **ninguna** dependencia fuera de esta lista sin preguntar antes.

- **Lenguaje / build**: Kotlin, Gradle Kotlin DSL, version catalog en `gradle/libs.versions.toml`.
- **UI app**: Jetpack Compose + Material 3.
- **Widgets**: Jetpack Glance (`androidx.glance:glance-appwidget`).
- **Persistencia**: Room (posiciones, watchlist, caché de cotizaciones, FX) + DataStore (preferencias).
- **Red**: Retrofit + OkHttp + kotlinx.serialization.
- **Background**: WorkManager (`PeriodicWorkRequest`, mínimo real 15 min).
- **DI**: Hilt.
- **Tests**: JUnit4 + kotlinx-coroutines-test.

### Toolchain

| Componente | Versión | Nota |
| --- | --- | --- |
| AGP | 9.3.1 | Lleva **Kotlin integrado**: no se aplica `org.jetbrains.kotlin.android` |
| Gradle | 9.3.1 | Mínimo exigido por AGP 9.1+ |
| Kotlin | 2.3.21 | Emparejado con KSP 2.3.11 (no hay KSP para 2.4.x todavía) |
| KSP | 2.3.11 | Room y Hilt procesan con KSP; **kapt está prohibido** (incompatible con el Kotlin integrado de AGP 9) |
| JDK | 21 (JBR de Android Studio) | `sourceCompatibility` / `targetCompatibility` = 17 |
| compileSdk / targetSdk | 37 | minSdk 26 |

`minSdk 26` implica que `java.time` está disponible de serie: **no hace falta desugaring**.

---

## 3. Arquitectura

Módulo único `:app`. Las capas son paquetes y la frontera se mantiene por convención
y por interfaces de repositorio.

```
dev.isaacru.bolsawidgets
├── domain/          Sin dependencias de Android, Room, Retrofit ni Compose
│   ├── model/       Quote, Candle, Position, WatchlistItem, FxRate, PortfolioSummary…
│   ├── calc/        PortfolioCalculator, CurrencyConverter — funciones puras
│   ├── provider/    QuoteProvider (interfaz) + ProviderId
│   └── repository/  Interfaces de repositorio
├── data/
│   ├── local/       Room: entities, DAOs, BolsaDatabase, mappers entity↔domain
│   ├── remote/      Retrofit: DTOs, mappers wire↔domain, implementaciones de QuoteProvider
│   └── repository/  Implementaciones de los repositorios
├── di/              Módulos de Hilt
├── ui/              Compose: pantallas, ViewModels, tema
└── widget/          Glance: providers, receivers, renderizado a bitmap
```

**Reglas de dependencia**

- `domain` no importa nada de `data`, `ui`, `widget`, Android ni librerías de terceros
  (salvo `javax.inject` y corrutinas). Debe poder testearse con JUnit puro.
- `ui` y `widget` dependen de `domain`, **nunca** de `data` directamente.
- `data` implementa las interfaces de `domain`.

### Convenciones de capa

- Repositorios: interfaz en `domain/repository`, implementación `…RepositoryImpl` en
  `data/repository`, enlazadas con `@Binds` en `di/RepositoryModule`.
- ViewModels exponen **`StateFlow<UiState>`**, nunca `LiveData` ni estado mutable público.
- Toda operación de disco o red va en un dispatcher inyectado (`@IoDispatcher`), nunca
  `Dispatchers.IO` a pelo, para que los tests puedan sustituirlo.
- La hora actual se lee siempre de un `java.time.Clock` inyectado, nunca con `Instant.now()`
  ni `System.currentTimeMillis()` directamente. Zona horaria del proyecto: `Europe/Madrid`.

---

## 4. Reglas de dominio

- **Divisa base: EUR.** Todo importe agregado de cartera se expresa en EUR.
- La conversión usa el **snapshot de FX cacheado**, incluido el coste de adquisición: la
  cartera se valora íntegramente a tipo de cambio de hoy.
- Varias `Position` del mismo `symbol` se agregan con **precio medio ponderado por cantidad**:
  `sum(cantidad × precio) / sum(cantidad)`. Nunca la media aritmética.
- La **variación del día** se mide siempre contra `previousClose` (cierre de la sesión
  anterior), no contra la apertura.
- `CurrencyConverter` normaliza las cotizaciones en unidades menores: Yahoo devuelve `GBp`
  (peniques) para varios valores de Londres. Sin esa normalización el error es de 100×.
- Una posición **sin cotización** (o sin tipo de cambio) aporta su coste de adquisición al
  valor total —para que el total no se quede corto en silencio— pero **P&L cero**, y su
  símbolo aparece en `PortfolioSummary.unpricedSymbols` para que la UI lo señale.

---

## 5. La fuente de datos es frágil

Yahoo Finance son endpoints públicos no documentados. Se asume que fallan.

- **User-Agent obligatorio**: sin cabecera de navegador Yahoo responde `429`.
- Timeouts cortos (connect 5 s, read 8 s, call 12 s). Un refresco colgado es peor que uno
  que reutiliza la caché.
- Reintentos con backoff exponencial **solo** en fallos transitorios (`IOException`, `429`,
  `5xx`). Un `404` no se reintenta.
- **Regla de oro**: ante cualquier error, el widget muestra el último dato cacheado en Room
  con su marca de hora. **Nunca** un mensaje de error en lugar del dato.
- `getQuotes` no falla en bloque: los símbolos que fallan se omiten del resultado y cada uno
  conserva su valor cacheado por separado.
- El endpoint de Yahoo sirve un símbolo por llamada, así que el batch es un fan-out con
  concurrencia limitada (4), no una petición agrupada.

---

## 6. Widgets (Glance)

- Glance/RemoteViews **no permite `Canvas` arbitrario**. El sparkline y el mapa de calor se
  dibujan en un `Bitmap` con `android.graphics.Canvas` en el worker o el provider y se
  muestran con `Image(ImageProvider(bitmap))`.
- El bitmap se dimensiona con `LocalSize.current`, nunca a tamaño fijo.
- Vigilar el límite de ~1,5 MB del bundle de `RemoteViews`: comprimir y dimensionar en
  consecuencia. Ante la duda, reducir resolución antes que arriesgar un `TransactionTooLargeException`.
- Cada widget lleva su propio botón de refresco manual.

---

## 7. Refresco

- `PeriodicWorkRequest` cada 15 min con `NetworkType.CONNECTED`.
- **Consciente de horario de mercado** (`MarketClock`, testeable): si todos los símbolos
  siguen mercados cerrados, se salta el fetch y se reutiliza la caché.
  - BME: 9:00–17:35, L-V
  - NYSE/NASDAQ: 15:30–22:00 hora de Madrid, L-V
  - Festivos ignorados en v1.
- FX se refresca como mucho **1 vez por hora**.

---

## 8. Tests

Obligatorios, con JUnit puro (sin Robolectric ni instrumentación):

- Precio medio ponderado y P&L (`PortfolioCalculatorTest`)
- Conversión de divisa (`CurrencyConverterTest`)
- `MarketClock` (fase 3)
- Parseo de la respuesta de Yahoo (`YahooMapperTest`) contra **JSON de ejemplo fijado** en
  `app/src/test/resources/yahoo/`

Los fixtures se capturaron del endpoint real y se recortaron a mano. `san_mc_1d_5m.json`
lleva una vela nula deliberada (hueco de subasta) para cubrir el filtrado.
**No se regeneran automáticamente**: son un contrato congelado.

Nombres de test en backticks describiendo el comportamiento, en inglés.

Ejecutar: `./gradlew :app:testDebugUnitTest`

---

## 9. Git

- Un commit por fase, [Conventional Commits](https://www.conventionalcommits.org/):
  `feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`.
- `local.properties` no se versiona. Los esquemas de Room (`app/schemas/`) **sí**.

---

## 10. No-objetivos

No implementar, y no proponer:

- Login, cuentas, nube, sincronización.
- Integración con brokers o importación automática de operaciones.
- Streaming en tiempo real ni WebSockets.
- Notificaciones o alertas de precio (v1).
- iOS, layouts específicos de tablet/foldable, i18n.

Si una decisión no está en la especificación ni en este documento, **preguntar** antes de
asumir. No añadir features no pedidas.

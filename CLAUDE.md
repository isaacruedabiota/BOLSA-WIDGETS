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
| Gradle | 9.5.0 | Mínimo exigido por AGP 9.3.1 |
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
- `hiltViewModel()` se importa de `androidx.hilt.lifecycle.viewmodel.compose`; el de
  `androidx.hilt.navigation.compose` está deprecado.
- Navegación con rutas type-safe (`@Serializable` en `ui/navigation/Destinations.kt`).
  Los nombres de esas clases se serializan: no renombrarlas a la ligera.
- Los mensajes puntuales de las pantallas viajan como `UiMessage` (un tipo, no un String),
  para que los ViewModels no toquen recursos de Android. El castellano vive en strings.xml.
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
- **Unidades menores**: Yahoo cotiza algunos valores de Londres en `GBp` (peniques, con `p`
  minúscula) y otros mercados en céntimos. `CurrencyConverter.normalizeCurrency` es la
  **única** fuente de verdad de esa tabla, y tanto los cálculos como el formateo tienen que
  pasar por ella. Ojo: `"GBp".uppercase()` es `"GBP"`, una divisa ISO perfectamente válida,
  así que cualquier código que haga `Currency.getInstance(x.uppercase())` sin filtrar antes
  muestra peniques como libras: error de 100×.
- Una posición **sin cotización** (o sin tipo de cambio) aporta su coste de adquisición al
  valor total —para que el total no se quede corto en silencio— pero **P&L cero**, y su
  símbolo aparece en `PortfolioSummary.unpricedSymbols` para que la UI lo señale.
- **Ningún símbolo entra en Room sin haber cotizado antes.** Tanto el alta en watchlist como
  el guardado de una posición pasan por `QuoteRepository.resolveSymbol`, que hace una llamada
  real al proveedor. Así no puede haber filas incotizables en la base de datos.

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
- El **buscador de símbolos** (`/v1/finance/search`) es un segundo endpoint no documentado y
  por tanto **opcional**: `SearchSymbolsUseCase` traga sus errores y los reporta como
  `suggestionsUnavailable`. El camino garantizado es siempre resolver el ticker exacto contra
  `v8/chart`. Si el buscador cae, la UI degrada a "escribe el ticker exacto", nunca se
  bloquea un alta.
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
- Cada widget lleva su propio botón de refresco manual, que **ignora el horario de mercado**
  igual que el de la app.
- Glance instancia los `GlanceAppWidget` el framework, no Hilt: las dependencias se obtienen
  con `WidgetEntryPoint` (`@EntryPoint` sobre `SingletonComponent`).
- Los widgets leen un **snapshot** de Room al abrir su sesión. Quien los redibuja es
  `WidgetUpdater`, llamado por el worker tras un fetch con cambios y por el observador de
  `BolsaWidgetsApp`, que vigila Room **y las preferencias** (el modo privacidad cambia lo
  que el widget puede imprimir).
- Los deep links de widget llevan el símbolo en la URI, nunca en un extra: los
  `PendingIntent` se deduplican con `Intent.filterEquals`, que ignora los extras, así que
  con extras todas las filas abrirían el mismo valor.
- `ui/common/Format.kt` y `ui/theme/Color.kt` son primitivas de presentación compartidas
  entre `ui` y `widget` a propósito: el formateo y la escala verde/rojo tienen que ser
  idénticos en la app y en la pantalla de inicio.

---

## 7. Refresco

- `PeriodicWorkRequest` con el intervalo de Ajustes (mínimo 15 min) y `NetworkType.CONNECTED`,
  encolado como trabajo único (`RefreshScheduler`) al arrancar la app y al cambiar el
  intervalo. WorkManager se inicializa **on demand** desde `BolsaWidgetsApp` para que Hilt
  pueda construir los workers; por eso el manifest quita `WorkManagerInitializer`.
- **Consciente de horario de mercado** (`MarketClock`, testeable): si todos los símbolos
  siguen mercados cerrados, el worker vuelve sin abrir un socket.
  - Las ventanas se declaran en la **zona horaria de cada plaza**, no en hora de Madrid.
    Europa y EE. UU. no cambian de horario de verano el mismo día, así que un par de
    semanas al año Nueva York abre a las 14:30 de Madrid en vez de a las 15:30. Declarando
    9:30–16:00 `America/New_York` eso sale gratis.
  - BME 9:00–17:35, Euronext 9:00–17:40, Xetra / Borsa Italiana / SIX 9:00–17:30,
    Londres 8:00–16:30 local. Todas L-V. Festivos ignorados en v1.
  - Un ticker que la tabla no reconoce (índices `^…`, pares `…=X`, guiones) se trata como
    **siempre abierto**: sobra-refrescar cuesta batería, no refrescar muestra datos falsos.
  - La ventana se alarga `CLOSING_GRACE` (20 min) tras el cierre para que una ejecución
    capture el precio de cierre oficial.
- El refresco **manual** (botón de la app) ignora el horario: un toque deliberado nunca se
  ignora en silencio.
- El worker nunca devuelve `Result.retry()`: el repositorio ya reintenta los fallos
  transitorios con backoff y el siguiente periodo está a minutos. Despertar la radio con el
  backoff de WorkManager sería gastar batería para nada.
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

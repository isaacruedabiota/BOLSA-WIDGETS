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
- El **tema** sale de `UserPreferences.themeMode` (Automático / Claro / Oscuro) y lo aplica
  `MainActivity`, no el propio `BolsaWidgetsTheme`: ese composable lo usan también las
  pantallas de configuración de los widgets, y esas no tienen por qué leer preferencias.
  "Automático" sigue al móvil, que es lo que una app debe hacer mientras no le digan otra
  cosa.
- Los mensajes puntuales de las pantallas viajan como `UiMessage` (un tipo, no un String),
  para que los ViewModels no toquen recursos de Android. El castellano vive en strings.xml.
- ViewModels exponen **`StateFlow<UiState>`**, nunca `LiveData` ni estado mutable público.
- Toda operación de disco o red va en un dispatcher inyectado (`@IoDispatcher`), nunca
  `Dispatchers.IO` a pelo, para que los tests puedan sustituirlo.
- La hora actual se lee siempre de un `java.time.Clock` inyectado, nunca con `Instant.now()`
  ni `System.currentTimeMillis()` directamente. Zona horaria del proyecto: `Europe/Madrid`.

---

## 4. Reglas de dominio

> **La cartera ya no está en la app.** No hay pestaña, ni editor de posiciones, ni widget de
> cartera, ni origen "cartera" en el mapa de calor: el usuario no lleva posiciones, lleva un
> plan de aportaciones. Lo que sobrevive es la capa de datos —`Position`, su tabla de Room,
> `PortfolioCalculator` y las filas `posicion` del CSV—, a propósito y sin consumidores: así
> no se destruye nada de lo ya guardado y una copia de seguridad antigua se sigue
> restaurando. Las reglas de abajo siguen valiendo para ese código.

- **Divisa base: EUR.** Todo importe agregado de cartera se expresa en EUR.
- La conversión usa el **snapshot de FX cacheado**, incluido el coste de adquisición: la
  cartera se valora íntegramente a tipo de cambio de hoy.
- Varias `Position` del mismo `symbol` se agregan con **precio medio ponderado por cantidad**:
  `sum(cantidad × precio) / sum(cantidad)`. Nunca la media aritmética.
- Un **plan de inversión** compra un importe fijo, no un número fijo de títulos: la cantidad
  es fraccionaria y cambia cada mes. Por eso "repetir compra" (`RepeatPurchase`) pide
  importe y precio y **deriva** la cantidad; lo que se arrastra del mes anterior es el
  importe, que es lo único que se repite.
- La **aportación** (`Contribution`) de un símbolo del seguimiento es un flujo, no una
  posición: importe en euros más cadencia (semanal o mensual), sin cantidad ni P&L. Se
  guarda tal y como el usuario la escribe —"50 a la semana" es como él lo piensa— y se
  compara siempre en `monthlyEur`. Un mes son **52/12 semanas**, no cuatro: llamarlo cuatro
  se queda corto un 8 % y eso basta para reordenar el mapa. Un `null` es "sin plan", nunca
  "cero euros".
- `Format.editable` existe aparte de `Format.plain` porque un número que va a un campo de
  texto **no puede llevar separador de miles**: en castellano es un punto, y al normalizar
  la coma decimal "2.450,00" deja de ser parseable.
- El **nombre de un valor lo manda el usuario**: `WatchlistRow.displayName` prefiere
  `item.name` y cae al del mercado solo si está vacío, así que renombrar algo se ve a la vez
  en la app y en **los tres widgets** —seguimiento, gráfico y mapa de calor—. Ojo con la
  diferencia entre "tiene nombre guardado" y "lo han renombrado": al añadir un valor se
  guarda el nombre del proveedor, así que `hasCustomName` compara con el `shortName` de la
  cotización. Sin esa comparación, todas las filas del widget pasan a enseñar el nombre
  largo del mercado en vez del ticker.
- Lo que imprime un widget en su única línea es `WatchlistRow.widgetLabel`: el nombre del
  usuario si lo hay, y si no el **ticker**, nunca el nombre del mercado. Es la regla la que
  se comparte entre widgets, no una copia del `if` en cada uno.
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
  - **Única excepción: la importación de CSV**, que no valida contra el proveedor a
    propósito. Restaurar una copia de seguridad tiene que funcionar sin red; un símbolo que
    ya no cotice aparecerá honestamente como "Sin precio" y en `unpricedSymbols` hasta el
    siguiente refresco.

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
- **Lo que Yahoo ya no da sin crumb**, comprobado: el screener POST (`/v1/finance/screener`)
  y las cotizaciones en lote (`v7/finance/quote`) responden `401 Invalid Crumb`. Lo único
  abierto para rankings son los **screeners predefinidos**
  (`/v1/finance/screener/predefined/saved?scrIds=day_gainers|day_losers`), y su universo es
  **EE. UU.**: no hay ranking europeo por una sola petición, y hacerlo símbolo a símbolo
  serían 35 llamadas. Por eso la pestaña Explorar dice de qué mercado habla en pantalla.
- El **buscador de símbolos** (`/v1/finance/search`) es un segundo endpoint no documentado y
  por tanto **opcional**: `SearchSymbolsUseCase` traga sus errores y los reporta como
  `suggestionsUnavailable`. El camino garantizado es siempre resolver el ticker exacto contra
  `v8/chart`. Si el buscador cae, la UI degrada a "escribe el ticker exacto", nunca se
  bloquea un alta.
- La lista de resultados enseña **la variación del día** de cada fila, y cuesta **una sola
  petición por búsqueda** (`spark`), no una por fila. Se pide después de dibujar la lista y
  la lista funciona sin ella; un símbolo del que no se sabe nada no enseña porcentaje, nunca
  un 0,00 % que sería una afirmación falsa sobre el mercado.
- Un valor **se mira antes de añadirlo**: tocar una sugerencia abre una ficha con precio,
  variación del día y el gráfico de la sesión, y de ahí sale el botón de añadir. La llamada
  que resuelve el símbolo es la misma que ya hacía el alta directa, así que mirar primero
  no cuesta nada extra hasta que se pide el gráfico, que es un toque deliberado más.
- La lista de sugerencias **solo se puede filtrar por tipo y por plaza**, que es lo único que
  se sabe de un símbolo antes de resolverlo. Precio, divisa y variación llegan con la
  cotización, **una llamada por símbolo**, y eso una lista de búsqueda no se lo puede
  permitir: por eso solo se muestran para el valor verificado. Y solo se ofrecen los filtros
  presentes en los resultados: un chip que no puede devolver nada es peor que ningún chip.
- El endpoint de cotización (`v8/chart`) sirve **un símbolo por llamada**, así que el batch
  es un fan-out con concurrencia limitada (4), no una petición agrupada.
- **Excepción: `v8/finance/spark`**, que sí acepta varios símbolos de golpe y sin crumb —es
  el que usa Yahoo para sus mini-gráficos—. Devuelve la serie del día y
  `fulldayChangePercent` ya calculado, pero **ni divisa ni nombre**, así que solo sirve para
  ponerle un porcentaje a algo ya identificado: la lista de búsqueda. La cotización completa
  sigue viniendo de `v8/chart`, que es lo que mantiene cierto el "nada entra en Room sin
  cotizar". `QuoteRepository.getDayChanges` es best-effort: un fallo es un mapa vacío.

---

## 6. Widgets (Glance)

- Glance/RemoteViews **no permite `Canvas` arbitrario**. El sparkline y el mapa de calor se
  dibujan en un `Bitmap` con `android.graphics.Canvas` en el worker o el provider y se
  muestran con `Image(ImageProvider(bitmap))`.
- El bitmap se dimensiona con `LocalSize.current`, nunca a tamaño fijo.
- Vigilar el límite de ~1,5 MB del bundle de `RemoteViews`: `BitmapBudget` recorta el
  tamaño antes de dibujar. Ante la duda, reducir resolución antes que arriesgar un
  `TransactionTooLargeException`.
- Los widgets de bitmap usan `SizeMode.Exact`, porque el dibujo tiene que hacerse al tamaño
  real y no a un tamaño redondeado.
- La geometría y el color van en funciones puras (`Treemap`, `HeatScale`, `BitmapBudget`)
  separadas del dibujo, para que se puedan testear sin Android.
- Al medir texto para una celda, **encogerlo hasta que quepa**, nunca omitirlo: dos tickers
  de la misma longitud no miden lo mismo y un umbral de todo o nada deja celdas mudas.
  Por eso `HeatmapEntry` lleva `symbol` y `label` por separado: si el nombre del usuario no
  entra ni encogido, la celda cae al ticker antes que quedarse muda.
- El estado por instancia de widget (símbolo y rango del sparkline, origen del mapa de
  calor) vive en `PreferencesGlanceStateDefinition`, y lo escribe la activity de
  configuración, que **lee el estado actual antes de dibujarse**: el lanzador la reabre
  para reconfigurar un widget ya colocado y arrancar en los valores por defecto cambiaría
  la configuración sin querer.
- El **área de una celda del mapa de calor es siempre dinero**, pero cuál depende del
  origen elegido: precio de un título (seguimiento) o aportación mensual (plan). Los dos
  **no se mezclan nunca** en un mismo mapa; en cuanto conviven dos medidas distintas, el
  área deja de significar nada.
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
- **No hay logos de empresa**: ninguna API de Yahoo los sirve, y traerlos de otro sitio
  costaría una dependencia nueva, una petición de imagen por fila y un hueco en blanco para
  todo lo que no esté en ese host. En su lugar, `Monogram` dibuja las iniciales sobre un
  color derivado del ticker: mismo símbolo, mismo color, siempre, sin red y sin depender de
  nadie.
- `ui/common/Format.kt` y `ui/theme/Color.kt` son primitivas de presentación compartidas
  entre `ui` y `widget` a propósito: el formateo y la escala verde/rojo tienen que ser
  idénticos en la app y en la pantalla de inicio.

---

## 7. Refresco

> **Regla de oro de la batería: nada de red fuera del worker.** Ni un redibujado de widget,
> ni una recomposición, ni abrir una pantalla. El worker periódico y los refrescos que pide
> el usuario a propósito son los únicos que abren un socket. Un redibujado ocurre por
> motivos que el usuario no ha pedido —reiniciar el lanzador, redimensionar, cualquier
> escritura en Room— y a cualquier hora; si uno de esos puede descargar, se descarga de
> madrugada.

- `PeriodicWorkRequest` con el intervalo de Ajustes (mínimo 15 min), `NetworkType.CONNECTED`
  y **`setRequiresBatteryNotLow`**, encolado como trabajo único (`RefreshScheduler`) al
  arrancar la app y al cambiar el intervalo. WorkManager se inicializa **on demand** desde
  `BolsaWidgetsApp` para que Hilt pueda construir los workers; por eso el manifest quita
  `WorkManagerInitializer`.
- **Sin widgets colocados, el worker no hace nada**: comprueba `AppWidgetManager` y vuelve
  antes de tocar disco o radio. El refresco en segundo plano existe para la pantalla de
  inicio; si no hay nada que mantener al día, la app ya se refresca al abrirse.
- **Consciente de horario de mercado** (`MarketClock`, testeable) **símbolo a símbolo**: se
  descartan los que siguen una plaza cerrada, y si no queda ninguno el worker vuelve sin
  abrir un socket. No es todo o nada: con Nueva York abierta y Madrid cerrada, pedir los
  valores del IBEX compra un precio que no puede haberse movido.
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
- El **ranking de Explorar** se pide al abrir la pestaña si su caché pasa de 15 minutos, o
  cuando el usuario toca actualizar. Nunca desde el worker: es una pantalla que alguien está
  mirando, no algo de lo que dependa un widget. Cambiar de pestaña no cuesta una petición.
- El worker nunca devuelve `Result.retry()`: el repositorio ya reintenta los fallos
  transitorios con backoff y el siguiente periodo está a minutos. Despertar la radio con el
  backoff de WorkManager sería gastar batería para nada.
- FX se refresca como mucho **1 vez por hora**, y solo para las divisas que hay en pantalla.
- **Las velas del sparkline las mantiene el worker**, no el widget: el widget dibuja de
  caché (`CACHE_ONLY`) y solo descarga si no hay nada guardado. Es la aplicación directa de
  la regla de oro, y es el único redibujo que el worker se reserva, porque la tabla de velas
  no la observa nadie.
- **Un solo sitio redibuja los widgets**: el observador de `BolsaWidgetsApp`. Y lo que
  colecta es una **firma de lo que los widgets imprimen** (símbolo, precio, variación,
  aportación, modo privacidad), no los datos: cada fetch con éxito reescribe su fila con
  marca de hora nueva aunque el precio no se haya movido, y sin la firma eso serían cuatro
  widgets redibujados —y un bitmap de mapa de calor— cada cuarto de hora, todo el día.

---

## 8. Copia de seguridad (CSV)

- **Un solo archivo** con cartera y seguimiento, distinguidos por la primera columna
  (`posicion` / `seguimiento`). Cabecera en español; es un archivo que el usuario abre.
- Las columnas **solo se añaden al final** y el parser lee por índice con `getOrNull`, así
  que un archivo escrito antes de que existiera una columna (p. ej. `aportacion`) se
  restaura igual: lo que falta es "sin dato", nunca un error.
- Decimales **siempre con punto** al escribir —la coma es el separador de campos— y se
  aceptan ambos al leer, porque las hojas de cálculo españolas reescriben con comas.
- Los saltos de línea se aplanan a espacios al exportar: un registro es una línea, y así
  el parser puede ser línea a línea.
- **Compartir una lista no es una copia de seguridad.** El CSV lleva todo —aportaciones y
  favoritos incluidos— y **reemplaza**; una lista compartida (`WatchlistShare`) lleva solo
  símbolo y nombre y **se suma** a la tuya. Lo que metes cada mes no sale del móvil por ahí,
  y aceptar la lista de un amigo no puede borrar la tuya. Sus símbolos pasan igualmente por
  `resolveSymbol`: la excepción de "no validar" es solo del CSV.
- **Importar reemplaza**, nunca fusiona, y avisa antes con el recuento. Fusionar
  duplicaría cada compra en la segunda importación: una posición escrita a mano no tiene
  identidad natural con la que deduplicar.
- Una fila ilegible se salta y se cuenta; nunca aborta el archivo entero.
- El acceso al archivo (SAF, `ContentResolver`) vive en la pantalla, no en el ViewModel:
  `BackupRepository` solo habla de texto, así el ViewModel no necesita `Context`.

---

## 9. Tests

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

## 10. Git

- Un commit por fase, [Conventional Commits](https://www.conventionalcommits.org/):
  `feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`.
- `local.properties` no se versiona. Los esquemas de Room (`app/schemas/`) **sí**.

---

## 11. No-objetivos

No implementar, y no proponer:

- Login, cuentas, nube, sincronización.
- Integración con brokers o importación automática de operaciones.
- Streaming en tiempo real ni WebSockets.
- Notificaciones o alertas de precio (v1).
- iOS, layouts específicos de tablet/foldable, i18n.

Si una decisión no está en la especificación ni en este documento, **preguntar** antes de
asumir. No añadir features no pedidas.

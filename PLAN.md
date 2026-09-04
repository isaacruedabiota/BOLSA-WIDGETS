# Plan de desarrollo — Bolsa Widgets

Estado a 23 de agosto de 2026.

| Fase | Contenido | Estado |
| --- | --- | --- |
| 1 | Proyecto base, Gradle, Hilt, Room, modelo de datos, `QuoteProvider` con Yahoo + tests | ✅ Completada |
| 2 | App Compose: cartera, watchlist, ajustes, contra datos reales | ✅ Completada |
| 3 | WorkManager + `MarketClock` + caché y política de fallback | ✅ Completada |
| 4 | Widgets 1 y 2 (watchlist y resumen de cartera), solo texto/layout | ✅ Completada |
| 5 | Widgets 3 y 4 (sparkline y mapa de calor), renderizado a bitmap | ✅ Completada |
| 6 | Detalle de valor con gráfico, CSV import/export, pulido | ✅ Completada |

Cada fase termina con un commit convencional y una parada para que compiles y valides.

---

## Fase 1 — Base ✅

**Entregado**

- Proyecto Gradle con version catalog, AGP 9.3.1 / Gradle 9.5.0 / Kotlin 2.3.21 / KSP 2.3.11,
  compileSdk y targetSdk 37, minSdk 26.
- Hilt cableado: `AppModule` (Clock, ZoneId Europe/Madrid, dispatchers), `DatabaseModule`,
  `NetworkModule`, `RepositoryModule`, `ProviderModule`.
- Room v1 con cuatro tablas: `positions`, `watchlist`, `cached_quotes`, `fx_rates`.
  Esquema exportado a `app/schemas/`.
- Modelo de dominio completo: `Quote`, `Candle`, `ChartRange`/`CandleInterval`, `Position`,
  `AggregatedPosition`, `WatchlistItem`, `FxRate`, `PositionValuation`, `PortfolioSummary`.
- `PortfolioCalculator`: agregación con precio medio ponderado, valoración en EUR,
  P&L del día y total, resumen de cartera.
- `CurrencyConverter`: pares directos, inversos, triangulación vía EUR y normalización de
  unidades menores (`GBp`).
- `QuoteProvider` con las cuatro operaciones de la spec.
  - `YahooQuoteProvider` (por defecto): quotes, candles y FX contra
    `query1.finance.yahoo.com/v8/finance/chart/{symbol}`, con User-Agent de navegador,
    timeouts cortos, reintentos con backoff y fan-out limitado a 4 peticiones simultáneas.
  - `TwelveDataQuoteProvider`: stub que falla de forma explícita, listo para implementar.
- Repositorios de cartera y watchlist sobre Room.
- `MainActivity` mínima con el tema Material 3 (solo prueba que el grafo de Hilt levanta).
- Tests: 34 casos en `PortfolioCalculatorTest`, `CurrencyConverterTest`, `YahooMapperTest`
  y `RetryTest`, con fixtures JSON reales fijados en `app/src/test/resources/yahoo/`.

**Fuera de alcance en esta fase (por diseño)**

- `QuoteRepository` con política de fallback a caché → fase 3.
- Preferencias en DataStore y selección de proveedor desde ajustes → fase 2.

---

## Fase 2 — App Compose ✅

**Entregado**

- `SettingsRepository` sobre DataStore: proveedor, modo privacidad, intervalo de refresco.
- `QuoteRepository` cache-first: las lecturas salen siempre de Room, las escrituras solo
  ocurren si el fetch va bien. Selección de proveedor con caída a Yahoo si el elegido no
  está configurado.
- Casos de uso: `ObservePortfolioUseCase`, `ObserveWatchlistUseCase`,
  `RefreshMarketDataUseCase` (con umbral de frescura) y `SearchSymbolsUseCase`.
- Navegación type-safe con `navigation-compose` y barra inferior de tres pestañas.
- **Cartera**: tarjeta de resumen (valor total, P&L día y total, invertido, hora de
  actualización), tarjetas por valor agregado que se despliegan mostrando sus compras
  individuales, aviso de símbolos sin cotización, alta/edición/borrado.
- **Seguimiento**: precio, variación con color y flecha, marca de hora, reordenar y quitar
  desde el menú de cada fila.
- **Ajustes**: proveedor, modo privacidad, intervalo de refresco.
- Buscador de símbolos compartido por ambas pantallas.

**Decisión sobre el buscador**: híbrido, no uno u otro. Toda alta se valida contra
`v8/chart` —el endpoint del que ya depende toda la app—, así que ningún símbolo incotizable
entra en Room. El buscador de Yahoo (`/v1/finance/search`) se añade encima como sugerencias
best-effort: si falla, la UI lo dice y sigue aceptando el ticker exacto.

**Validado en emulador** (Pixel, API 36) contra la API real: alta de SAN.MC y AAPL34.SA en
seguimiento, posición de 10 AAPL a 150 US$ valorada en euros con FX real, y modo privacidad
ocultando importes. Sin crashes.

**Corregido durante la validación**

- El buscador conservaba la consulta anterior al reabrirse (el ViewModel sobrevive al cierre
  de la hoja). Ahora se limpia en cada apertura.
- El campo Divisa era editable y por defecto EUR, así que una posición en AAPL se habría
  guardado en euros. Ahora es de solo lectura y se deriva del símbolo verificado.
- `Format` pasaba la divisa a mayúsculas antes de buscarla en ISO, y `"GBp".uppercase()` es
  `"GBP"`: mostraba peniques como libras. Cubierto ahora por `FormatTest`.
- Al resolver un símbolo no se pedía su tipo de cambio, así que una posición en divisa nueva
  aparecía como "sin cotización" hasta el siguiente refresco. Ahora cada símbolo trae su FX.

**Fuera de alcance en esta fase (por diseño)**

- Import/export CSV → fase 6.
- Conciencia de horario de mercado y WorkManager → fase 3.

---

## Fase 3 — Refresco ✅

**Entregado**

- `Market`: tabla de plazas con su sesión declarada **en la zona horaria de cada una**
  (BME, Euronext, Xetra, Borsa Italiana, SIX, Londres y NYSE/NASDAQ), más el mapeo desde
  el sufijo del ticker de Yahoo.
- `MarketClock`: `isOpen`, `shouldFetch` y `marketsOf`, con `Clock` inyectado.
- `RefreshMarketDataUseCase` acepta `respectMarketHours`: el worker lo pasa a `true` y
  vuelve sin abrir un socket cuando todo está cerrado; el botón manual lo pasa a `false`.
- `RefreshQuotesWorker` (`@HiltWorker`) y `RefreshScheduler`, con trabajo único
  `bolsa-periodic-refresh`, `NetworkType.CONNECTED` y el intervalo de Ajustes.
  Se reencola al arrancar la app y al cambiar el intervalo.
- WorkManager inicializado on-demand desde `BolsaWidgetsApp` (`Configuration.Provider`),
  con `WorkManagerInitializer` quitado del manifest para que Hilt construya los workers.
- 18 tests nuevos: `MarketClockTest` y `RefreshMarketDataUseCaseTest`.

**Decisiones tomadas**

- La spec solo fija BME y NYSE/NASDAQ. Como sigues ETF europeos, añadí las plazas europeas
  que hacen falta para que el criterio "fuera de horario no se consume red" se cumpla de
  verdad. Un sufijo que la tabla no conoce se trata como **siempre abierto**: sobra-refrescar
  cuesta batería, no refrescar muestra datos falsos.
- Las ventanas van en hora local de cada plaza en vez de en hora de Madrid, porque Europa y
  EE. UU. no cambian de horario de verano el mismo día. `MarketClockTest` cubre esa semana.
- 20 minutos de gracia tras el cierre, para que una ejecución capture el cierre oficial.
- El worker nunca devuelve `Result.retry()`. El repositorio ya reintenta lo transitorio y el
  siguiente periodo está a minutos.

El fallback a caché ya estaba en su sitio desde la fase 2: las lecturas salen de Room y un
fetch fallido no borra nada. El tope de 1 refresco de FX por hora también.

---

## Fase 4 — Widgets de texto ✅

**Entregado**

- **Seguimiento** (4x2 y 4x4): `LazyColumn` de Glance con símbolo, precio, variación del
  día con flecha y color. A 4x4 añade el nombre del valor bajo el ticker. Tap en fila abre
  la app con un deep link `bolsawidgets://symbol/{ticker}`.
- **Cartera** (2x2 y 4x2): valor total en EUR, P&L del día y total, hora del último dato.
  A 2x2 deja solo los porcentajes en vez de truncar los importes. Respeta el modo
  privacidad ocultando los euros.
- Botón de refresco en la cabecera de ambos, con `ActionCallback`. Ignora el horario de
  mercado, igual que el botón de dentro de la app.
- `WidgetEntryPoint` (Hilt `@EntryPoint`) porque Glance instancia los widgets el framework,
  no Hilt. `WidgetUpdater` los redibuja.
- Se redibujan desde el worker tras un fetch con cambios, y desde un observador en
  `BolsaWidgetsApp` que vigila Room y las preferencias, para que cualquier edición en la
  app se refleje sin esperar al siguiente tick.

**Decisiones tomadas**

- Los datos se leen como snapshot al abrir la sesión de Glance en vez de recolectar flujos
  dentro de la composición: más predecible, y el redibujado es explícito.
- El símbolo del deep link viaja en la URI y no en un extra, porque los `PendingIntent` se
  deduplican con `Intent.filterEquals`, que ignora los extras. Con extras, todas las filas
  habrían abierto el mismo valor.
- Las flechas ▲▼ son texto, no iconos: heredan color y tamaño del dato y no ocupan nada en
  el bundle de `RemoteViews`.
- El modo privacidad tapa los importes de **cartera**, no los precios de mercado del widget
  de seguimiento: esos son públicos y no dicen cuánto tienes.

**Validado en emulador**: los dos widgets añadidos a la pantalla de inicio desde el selector
del launcher, mostrando datos reales (SAN.MC 12,55 € ▲+2,65 %, ITX.MC 58,22 € ▲+1,11 %;
cartera 1.255,00 € con Día ▲+2,65 % y Total ▲+25,50 %). Botón de refresco disparando
peticiones reales, tap en fila abriendo la app, y el widget de cartera actualizándose solo
al guardar una posición.

**Corregido durante la validación**

- El observador solo vigilaba Room, así que activar el modo privacidad no redibujaba los
  widgets: seguían enseñando los euros hasta el siguiente precio. Ahora también observa las
  preferencias.

**Pendiente por diseño**: el tap en una fila aterriza en Seguimiento porque la pantalla de
detalle es de la fase 6. El deep link ya lleva el símbolo y solo hay que enrutarlo.

---

## Fase 5 — Widgets con bitmap ✅

**Entregado**

- **Valor con gráfico** (2x2, 4x2): un ticker configurable con precio grande, variación y
  sparkline. `SparklineConfigActivity` se lanza al colocar el widget y reutiliza el
  buscador de símbolos; el rango (1D/1S/1M/1A) se elige ahí. El widget es
  `reconfigurable`, así que se puede cambiar después desde el lápiz del launcher.
- **Mapa de calor** (4x4): treemap tipo Finviz. Área por peso en cartera, color por la
  variación del día en una escala roja→gris→verde saturada a ±3 %.
- `SparklineRenderer` y `HeatmapRenderer` dibujan con `android.graphics.Canvas` sobre un
  `Bitmap`, dimensionado desde `LocalSize.current` y la densidad real.
- `BitmapBudget` recorta el tamaño para no pasar del bundle de `RemoteViews`.
- Caché de velas en Room (tabla `cached_candles`, **migración 1→2**): el sparkline dibuja
  sin red y solo refetch cuando la serie supera la antigüedad de su rango
  (15 min para 1D, 1 h para 1S, 6 h para 1M, 24 h para 1A).
- 20 tests nuevos: `TreemapTest`, `HeatScaleTest`, `BitmapBudgetTest`.

**Decisiones tomadas**

- El treemap es **squarified**, no slice-and-dice: con doce posiciones iguales el peor
  ratio de aspecto baja de 12 a menos de 3, que es la diferencia entre un mapa legible y
  una persiana. Cubierto por un test.
- La serie de velas se guarda como **una fila JSON por (símbolo, rango)** en vez de una
  fila por vela: solo se lee y escribe entera, y un 1A serían cientos de filas por nada.
- `SizeMode.Exact` en los dos widgets de bitmap: redondear a tamaños predefinidos
  emborronaría el dibujo o desperdiciaría píxeles.
- Las velas se piden dentro de `provideGlance` con la política cache-first, en vez de
  que el worker sepa qué sparklines hay configurados. Menos acoplamiento y el coste está
  acotado por la antigüedad máxima.

**Validado en emulador**: migración 1→2 sobre una base de datos con datos (posiciones
intactas, tabla nueva creada); mapa de calor con tres posiciones reales (AAPL 52,26 % en
rojo apagado, SAN.MC 24,76 % verde intenso, ITX.MC 22,98 % verde suave) y áreas que
coinciden con los pesos que muestra la app; sparkline de SAN.MC a 1M con su gráfico real.

**Corregido durante la validación**

- El mapa de calor dejaba celdas **sin etiqueta**: medía el texto y, si no cabía, no
  dibujaba nada. Dos tickers de la misma longitud no miden lo mismo ("SAN.MC" es más ancho
  que "ITX.MC"), así que unas celdas salían etiquetadas y otras no. Ahora el texto se
  encoge hasta caber.

---

## Fase 6 — Detalle y pulido ✅

**Entregado**

- **Detalle de valor**: precio, variación, selector de rango 1D/1S/1M/1A, gráfico, mínimo
  y máximo del periodo, y la tarjeta "Tu posición" con cantidad, precio medio, valor,
  invertido y P&L cuando tienes el valor en cartera. Respeta el modo privacidad.
- `PriceChart` dibujado con **Compose Canvas**, con el mismo lenguaje visual que el
  sparkline del widget (línea, degradado debajo, referencia discontinua del cierre
  anterior y punto en el último dato).
- **Import/export CSV** en Ajustes, vía Storage Access Framework.
- Se llega al detalle desde tres sitios: tocar una fila de Seguimiento, el botón de
  gráfico en una tarjeta de Cartera, y el **deep link del widget**, que ya no aterriza en
  Seguimiento sino en el valor concreto (el TODO que quedaba de la fase 4).
- 14 tests nuevos de `PortfolioCsv`.

**Decisiones tomadas**

- **Compose Canvas, no Vico.** Es una sola pantalla, la geometría ya estaba resuelta para
  el sparkline, y evita meter una dependencia fuera del stack fijado.
- **Un único CSV** para cartera y seguimiento, distinguidos por la primera columna, para
  que una copia de seguridad sea un solo archivo que guardar y un solo archivo que
  restaurar.
- **Importar reemplaza, no fusiona**, y lo dice antes de hacerlo: el archivo se analiza
  primero y un diálogo muestra cuántas posiciones y valores trae y cuántas filas ilegibles
  se van a ignorar. Fusionar duplicaría cada compra en la segunda importación, porque una
  posición escrita a mano no tiene identidad natural.
- Los decimales se escriben siempre con punto (la coma es el separador de campos) pero se
  aceptan ambos al leer, porque una hoja de cálculo española reescribe el archivo con
  comas.
- Un salto de línea en las notas se aplana a un espacio al exportar, de modo que un
  registro es siempre una línea y el parser puede seguir siendo línea a línea.
- Una fila mal formada **no aborta la importación**: se salta, se cuenta y se avisa.
- La importación **no valida los símbolos** contra el proveedor, a diferencia del alta
  manual: restaurar una copia tiene que funcionar sin red. Un símbolo que ya no cotice sale
  como "Sin precio" hasta el siguiente refresco.

**Validado en emulador**: detalle de SAN.MC con gráfico intradía real y la tarjeta de
posición cuadrando (100 × 12,706 = 1.270,60 €, P&L total +27,06 %); cambio a 1A trayendo un
año de velas (mín 8,05 €, máx 12,91 €, +53,40 %); exportación a `/sdcard/Download` con las
3 posiciones y 2 valores; importación de un CSV distinto que **reemplazó** el contenido
(2 posiciones, 1 valor), conservó una nota con coma entrecomillada y saltó la fila rota;
y el deep link de una fila de widget abriendo el detalle de IWDA.AS.

**Corregido durante la validación**

- La pantalla de detalle **crasheaba al abrirse**: en el primer frame el gráfico aún no
  había empezado a cargar, así que el `when` caía en la rama de dibujar con la lista de
  velas vacía y `closes.last()` lanzaba. Ahora el estado inicial ya es "cargando" y la rama
  de dibujo comprueba los datos en vez de fiarse del flag.
- Concordancia de plurales: el diálogo decía "1 valores" y "1 filas". Convertido a
  `<plurals>`, que es la herramienta correcta, y de paso se eliminó el `if` de
  singular/plural que había en dos pantallas.

---

## Después de la v1

**Repetir compra** (aportación mensual de un plan de inversión). Desde el menú de cada
tarjeta de Cartera: prerrellena el importe del último lote y el precio actual, deriva los
títulos y añade la compra con la fecha de hoy. La cantidad se deriva porque en un plan de
TR lo fijo es el importe, no los títulos.

Corregido al validarlo: el importe se prerrellenaba con `Format.plain`, que mete separador
de miles, así que "2.450,00" no se podía volver a parsear y el botón salía deshabilitado.
Ahora hay `Format.editable` para los números que van a un campo editable, con un test que
comprueba el ida y vuelta.

**Origen del mapa de calor**. El mapa puede dibujarse desde la cartera (el área de cada
celda es su peso) o desde la lista de seguimiento (todas las celdas iguales, solo importa
el color). Es una elección por instancia de widget, no una mezcla: en cuanto conviven
celdas ponderadas con celdas de tamaño fijo, el área deja de significar nada. Se elige en
una pantalla de configuración, como el sparkline, y el widget se declara `reconfigurable`.

Corregido al validarlo: las dos pantallas de configuración se reabren desde el lápiz del
lanzador para reconfigurar un widget ya colocado, y ninguna leía el estado actual —
proponían los valores por defecto, así que "Guardar" cambiaba la configuración sin
querer. Ahora ambas esperan a leer el estado del widget antes de dibujarse, y el botón
dice "Guardar" en vez de "Añadir widget", que solo era cierto la primera vez.

**Rediseño del mapa de calor**. Oscuro siempre (ignora el tema del sistema: el mapa es un
bloque de color y un marco blanco sería lo más brillante de la pantalla), celdas pegadas
sin hueco ni borde, y una rampa de dos colores: verde si sube, rojo si baja, de un tinte
casi negro cuando apenas se ha movido a un color saturado en el ±3 % donde satura. El gris
neutro desapareció; era una tercera cosa que leer en un mapa que se tiene que entender sin
leer. El bitmap se dibuja sin antialiasing y con los bordes redondeados hacia fuera, porque
si no, entre celda y celda se cuela una línea de fondo que es justo la rejilla que se
quería quitar.

Después se le quitó también la cabecera: el mapa ocupa el widget entero, esquina a esquina,
y el título ya solo existe como descripción para el lector de pantalla —los tickers de las
celdas dicen lo que es—. El refresco manual no se pierde: flota sobre la esquina superior
en blanco translúcido, que es lo único que se ve igual de bien sobre una celda encendida
que sobre una apagada.

Y el área pasó a medir valor también en el modo seguimiento: la celda de cada símbolo es el
precio de un título convertido a euros con el snapshot de FX, porque un valor que no tienes
no tiene más "cuánto vale" que eso. Efecto secundario asumido: con un ETF de 127 € al lado
de una acción de 3,68 €, la pequeña se queda en un 2 % del área y pierde la etiqueta. Es lo
que pasa cuando el área es honesta; la alternativa sería mentir sobre el tamaño.

---

## Criterios de aceptación v1

- [ ] 5 posiciones y 10 símbolos de watchlist añadidos a mano.
- [ ] Los 4 widgets se añaden a la pantalla de inicio, se configuran y muestran datos reales.
- [ ] En modo avión los widgets siguen mostrando el último dato con su marca de hora.
- [ ] Fuera de horario de mercado no se consume red.
- [ ] Consumo de batería del worker despreciable (Battery Historian).

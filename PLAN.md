# Plan de desarrollo — Bolsa Widgets

Estado a 23 de agosto de 2026.

| Fase | Contenido | Estado |
| --- | --- | --- |
| 1 | Proyecto base, Gradle, Hilt, Room, modelo de datos, `QuoteProvider` con Yahoo + tests | ✅ Completada |
| 2 | App Compose: cartera, watchlist, ajustes, contra datos reales | ⬜ Pendiente |
| 3 | WorkManager + `MarketClock` + caché y política de fallback | ⬜ Pendiente |
| 4 | Widgets 1 y 2 (watchlist y resumen de cartera), solo texto/layout | ⬜ Pendiente |
| 5 | Widgets 3 y 4 (sparkline y mapa de calor), renderizado a bitmap | ⬜ Pendiente |
| 6 | Detalle de valor con gráfico, CSV import/export, pulido | ⬜ Pendiente |

Cada fase termina con un commit convencional y una parada para que compiles y valides.

---

## Fase 1 — Base ✅

**Entregado**

- Proyecto Gradle con version catalog, AGP 9.3.1 / Gradle 9.3.1 / Kotlin 2.3.21 / KSP 2.3.11,
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
- Tests: 33 casos en `PortfolioCalculatorTest`, `CurrencyConverterTest`, `YahooMapperTest`
  y `RetryTest`, con fixtures JSON reales fijados en `app/src/test/resources/yahoo/`.

**Fuera de alcance en esta fase (por diseño)**

- `QuoteRepository` con política de fallback a caché → fase 3.
- Preferencias en DataStore y selección de proveedor desde ajustes → fase 2.

---

## Fase 2 — App Compose ⬜

- DataStore de preferencias: proveedor de datos, modo privacidad, intervalo de refresco.
- `QuoteRepository` básico (fetch + escritura en caché) para alimentar las pantallas.
- Navegación con `navigation-compose`: Cartera / Watchlist / Ajustes.
- **Cartera**: lista de posiciones agregadas con valor y P&L, alta/edición/borrado manual.
- **Watchlist**: añadir/quitar/reordenar, con buscador de símbolos.
- **Ajustes**: proveedor, modo privacidad, intervalo de refresco.
- ViewModels con `StateFlow`.

**A decidir contigo antes de empezar**: de dónde sale el buscador de símbolos. Yahoo tiene
un endpoint de búsqueda (`/v1/finance/search`) no documentado; la alternativa es introducir
el ticker a mano y validarlo con una llamada a `getQuote`.

## Fase 3 — Refresco ⬜

- `MarketClock` testeable (BME 9:00–17:35, NYSE/NASDAQ 15:30–22:00 hora de Madrid, L-V).
- `PeriodicWorkRequest` cada 15 min con `NetworkType.CONNECTED`, vía `hilt-work`.
- Política de fallback: red → caché Room, nunca error en el widget.
- FX refrescado como mucho 1 vez/hora.
- Tests de `MarketClock`.

## Fase 4 — Widgets de texto ⬜

- **Watchlist** (4x2, 4x4): `LazyColumn` de Glance, precio, variación %, color y flecha.
  Tap en fila abre el detalle del valor.
- **Resumen de cartera** (2x2, 4x2): valor total EUR, P&L día y total (€ y %), hora del
  último refresco, modo privacidad.
- Botón de refresco manual en cada widget.

## Fase 5 — Widgets con bitmap ⬜

- **Valor + sparkline** (2x2, 4x2): configuración de símbolo y rango (1D/1S/1M/1A) al añadir.
- **Mapa de calor** (4x4): treemap tipo Finviz, área por peso, color por variación del día
  (escala roja→gris→verde saturada en ±3%).
- Renderizado a `Bitmap` con `android.graphics.Canvas`, escalado con `LocalSize.current`,
  vigilando el límite de ~1,5 MB de `RemoteViews`.

## Fase 6 — Detalle y pulido ⬜

- Pantalla de detalle de valor con gráfico.
  **A decidir contigo**: Compose Canvas a mano o añadir Vico como dependencia.
- Import/export de cartera y watchlist en CSV.
- Pulido general.

---

## Criterios de aceptación v1

- [ ] 5 posiciones y 10 símbolos de watchlist añadidos a mano.
- [ ] Los 4 widgets se añaden a la pantalla de inicio, se configuran y muestran datos reales.
- [ ] En modo avión los widgets siguen mostrando el último dato con su marca de hora.
- [ ] Fuera de horario de mercado no se consume red.
- [ ] Consumo de batería del worker despreciable (Battery Historian).

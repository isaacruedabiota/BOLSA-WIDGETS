# Plan de desarrollo — Bolsa Widgets

Estado a 23 de agosto de 2026.

| Fase | Contenido | Estado |
| --- | --- | --- |
| 1 | Proyecto base, Gradle, Hilt, Room, modelo de datos, `QuoteProvider` con Yahoo + tests | ✅ Completada |
| 2 | App Compose: cartera, watchlist, ajustes, contra datos reales | ✅ Completada |
| 3 | WorkManager + `MarketClock` + caché y política de fallback | ⬜ Pendiente |
| 4 | Widgets 1 y 2 (watchlist y resumen de cartera), solo texto/layout | ⬜ Pendiente |
| 5 | Widgets 3 y 4 (sparkline y mapa de calor), renderizado a bitmap | ⬜ Pendiente |
| 6 | Detalle de valor con gráfico, CSV import/export, pulido | ⬜ Pendiente |

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

## Fase 3 — Refresco ⬜

- `MarketClock` testeable (BME 9:00–17:35, NYSE/NASDAQ 15:30–22:00 hora de Madrid, L-V).
- `PeriodicWorkRequest` con el intervalo elegido en Ajustes y `NetworkType.CONNECTED`,
  vía `hilt-work`. Reprogramar el worker cuando cambie el intervalo.
- Envolver `RefreshMarketDataUseCase` con la comprobación de horario: si todos los símbolos
  siguen mercados cerrados, no se toca la red.
- Tests de `MarketClock`.

El fallback a caché ya está en su sitio desde la fase 2: las lecturas salen de Room y un
fetch fallido no borra nada. El tope de 1 refresco de FX por hora también.

---

## Fase 4 — Widgets de texto ⬜

- **Watchlist** (4x2, 4x4): `LazyColumn` de Glance, precio, variación %, color y flecha.
  Tap en fila abre el detalle del valor.
- **Resumen de cartera** (2x2, 4x2): valor total EUR, P&L día y total (€ y %), hora del
  último refresco, modo privacidad.
- Botón de refresco manual en cada widget.

---

## Fase 5 — Widgets con bitmap ⬜

- **Valor + sparkline** (2x2, 4x2): configuración de símbolo y rango (1D/1S/1M/1A) al añadir.
- **Mapa de calor** (4x4): treemap tipo Finviz, área por peso, color por variación del día
  (escala roja→gris→verde saturada en ±3%).
- Renderizado a `Bitmap` con `android.graphics.Canvas`, escalado con `LocalSize.current`,
  vigilando el límite de ~1,5 MB de `RemoteViews`.

---

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

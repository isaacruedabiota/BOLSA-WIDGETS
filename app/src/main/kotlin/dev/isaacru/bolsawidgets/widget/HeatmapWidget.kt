package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.ui.theme.HeatBackground
import dev.isaacru.bolsawidgets.ui.theme.HeatOnBackground
import dev.isaacru.bolsawidgets.ui.theme.HeatOverlay
import dev.isaacru.bolsawidgets.widget.render.BitmapBudget
import dev.isaacru.bolsawidgets.widget.render.HeatmapEntry
import dev.isaacru.bolsawidgets.widget.render.HeatmapRenderer
import kotlinx.coroutines.flow.first

/** What the map is drawn from. Either way the area of a tile is what it is worth. */
enum class HeatmapSource {
    /** Positions, tile area proportional to what the holding is worth in euros. */
    PORTFOLIO,

    /**
     * Followed symbols, tile area proportional to the price of one share in euros, which
     * is the only value a symbol you do not hold has.
     */
    WATCHLIST,
}

/**
 * Finviz-style heat map: colour by the day's move, area by value.
 *
 * The source is per-instance state chosen when the widget is placed, because the two modes
 * measure different things. What a holding is worth and what one share costs are not the
 * same quantity, so putting both on one map would make the area meaningless.
 *
 * Drawn to a bitmap because RemoteViews has no Canvas of its own, and sized from the real
 * widget size so it stays sharp when resized.
 */
class HeatmapWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        // Widgets placed before the setting existed keep their original behaviour.
        val source = preferences[KEY_SOURCE]
            ?.let { stored -> HeatmapSource.entries.firstOrNull { it.name == stored } }
            ?: HeatmapSource.PORTFOLIO

        val entryPoint = WidgetEntryPoint.from(context)
        val entries = when (source) {
            HeatmapSource.PORTFOLIO -> entryPoint.observePortfolio().invoke().first().toEntries()
            HeatmapSource.WATCHLIST -> {
                // Prices come in whatever currency each market quotes in, so the map needs
                // the FX snapshot to put them all on one scale before comparing areas.
                val converter = entryPoint.quoteRepository().observeConverter().first()
                entryPoint.observeWatchlist().invoke().first().toEntries(converter)
            }
        }

        provideContent {
            GlanceTheme {
                HeatmapContent(entries = entries, source = source)
            }
        }
    }

    companion object {
        val KEY_SOURCE = stringPreferencesKey("heatmap_source")
    }
}

private fun PortfolioSummary.toEntries(): List<HeatmapEntry> = positions
    // A tile with no area cannot be drawn, and an unpriced position has no meaningful
    // colour either; both are already flagged inside the app.
    .filter { it.marketValueEur > 0.0 }
    .map {
        HeatmapEntry(
            symbol = it.position.symbol,
            weight = it.marketValueEur,
            changePercent = if (it.isPriced) it.dayPnlPercent else 0.0,
        )
    }

private fun List<WatchlistRow>.toEntries(
    converter: CurrencyConverter,
): List<HeatmapEntry> = mapNotNull { row ->
    // A symbol with no quote has no colour to show, so it is left out rather than drawn
    // grey and read as "flat today".
    val quote = row.quote ?: return@mapNotNull null
    val normalized = CurrencyConverter.normalizeCurrency(quote.currency)
    val priceEur = converter.toEur(quote.price, quote.currency)
        // No rate cached for that currency yet. A tile sized in its own currency is off by
        // the exchange rate; leaving the symbol out of the map would be worse. The minor
        // unit is already folded in, which is the error that would actually matter: being
        // wrong by 100x reorders the map, being wrong by 10% does not.
        ?: (quote.price * normalized.minorUnitFactor)
    HeatmapEntry(symbol = row.symbol, weight = priceEur, changePercent = quote.changePercent)
}

class HeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapWidget()
}

@Composable
private fun HeatmapContent(entries: List<HeatmapEntry>, source: HeatmapSource) {
    val context = LocalContext.current
    val size = LocalSize.current
    // No title on screen, so the map says which one it is to a screen reader instead.
    val label = context.getString(
        when (source) {
            HeatmapSource.PORTFOLIO -> R.string.widget_heatmap_label
            HeatmapSource.WATCHLIST -> R.string.widget_heatmap_label_watchlist
        },
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // The map keeps its own dark ground in both themes: it is a block of colour,
            // and a white frame around it would be the brightest thing on the screen.
            .background(ColorProvider(HeatBackground))
            .cornerRadius(CORNER_DP.dp)
            .clickable(actionStartActivity(openPortfolioIntent(context))),
    ) {
        if (entries.isEmpty()) {
            EmptyMessage(
                text = context.getString(
                    when (source) {
                        HeatmapSource.PORTFOLIO -> R.string.widget_empty_portfolio
                        HeatmapSource.WATCHLIST -> R.string.widget_empty_watchlist
                    },
                ),
                tint = ColorProvider(HeatOnBackground),
            )
        } else {
            // Corner to corner: with no header there is nothing left to make room for,
            // and the tickers inside the tiles already say what the map is.
            val widthDp = size.width.value.coerceAtLeast(48f)
            val heightDp = size.height.value.coerceAtLeast(48f)
            val density = context.resources.displayMetrics.density
            val pixels = BitmapBudget.sizeFor(widthDp, heightDp, density)
            val bitmap = HeatmapRenderer.render(
                entries = entries,
                size = pixels,
                // Expressed in the bitmap's own pixels, which stop being screen pixels as
                // soon as the budget scales the drawing down.
                cornerRadiusPx = CORNER_DP * pixels.width / widthDp,
            )

            Image(
                provider = ImageProvider(bitmap),
                contentDescription = label,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }

        // The manual refresh has to live somewhere, so it floats over the top corner:
        // small, half-transparent, and out of the way of the labels, which are centred.
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = context.getString(R.string.widget_refresh),
                colorFilter = ColorFilter.tint(ColorProvider(HeatOverlay)),
                modifier = GlanceModifier
                    .size(18.dp)
                    .clickable(actionRunCallback<RefreshWidgetsAction>()),
            )
        }
    }
}

private const val CORNER_DP = 16f

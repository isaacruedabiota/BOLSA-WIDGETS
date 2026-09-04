package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.widget.render.BitmapBudget
import dev.isaacru.bolsawidgets.widget.render.HeatmapEntry
import dev.isaacru.bolsawidgets.widget.render.HeatmapRenderer
import kotlinx.coroutines.flow.first

/** What the map is drawn from. Area only means something in [PORTFOLIO]. */
enum class HeatmapSource {
    /** Positions, tile area proportional to weight in the portfolio. */
    PORTFOLIO,

    /** Followed symbols, every tile the same size because there is no weight. */
    WATCHLIST,
}

/**
 * Finviz-style heat map: colour by the day's move, area by weight in the portfolio.
 *
 * The source is per-instance state chosen when the widget is placed, because the two
 * modes cannot be mixed: as soon as equal-sized watchlist tiles sit next to weighted
 * position tiles, the area of a tile stops meaning anything.
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
            HeatmapSource.WATCHLIST -> entryPoint.observeWatchlist().invoke().first().toEntries()
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

private fun List<WatchlistRow>.toEntries(): List<HeatmapEntry> = mapNotNull { row ->
    // A symbol with no quote has no colour to show, so it is left out rather than drawn
    // grey and read as "flat today".
    val quote = row.quote ?: return@mapNotNull null
    HeatmapEntry(symbol = row.symbol, weight = 1.0, changePercent = quote.changePercent)
}

class HeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapWidget()
}

@Composable
private fun HeatmapContent(entries: List<HeatmapEntry>, source: HeatmapSource) {
    val context = LocalContext.current
    val size = LocalSize.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity(openPortfolioIntent(context))),
    ) {
        WidgetHeader(
            title = context.getString(
                when (source) {
                    HeatmapSource.PORTFOLIO -> R.string.widget_heatmap_label
                    HeatmapSource.WATCHLIST -> R.string.widget_heatmap_label_watchlist
                },
            ),
        )

        if (entries.isEmpty()) {
            EmptyMessage(
                context.getString(
                    when (source) {
                        HeatmapSource.PORTFOLIO -> R.string.widget_empty_portfolio
                        HeatmapSource.WATCHLIST -> R.string.widget_empty_watchlist
                    },
                ),
            )
            return@Column
        }

        val widthDp = (size.width.value - HORIZONTAL_PADDING_DP).coerceAtLeast(48f)
        val heightDp = (size.height.value - HEADER_DP).coerceAtLeast(48f)
        val density = context.resources.displayMetrics.density
        val bitmap = HeatmapRenderer.render(
            entries = entries,
            size = BitmapBudget.sizeFor(widthDp, heightDp, density),
        )

        Image(
            provider = ImageProvider(bitmap),
            contentDescription = context.getString(R.string.widget_heatmap_label),
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

private const val HEADER_DP = 34f
private const val HORIZONTAL_PADDING_DP = 20f

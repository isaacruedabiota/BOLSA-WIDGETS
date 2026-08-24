package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.widget.render.BitmapBudget
import dev.isaacru.bolsawidgets.widget.render.HeatmapEntry
import dev.isaacru.bolsawidgets.widget.render.HeatmapRenderer
import kotlinx.coroutines.flow.first

/**
 * Finviz-style heat map of the portfolio: tile area by weight, colour by the day's move.
 *
 * Drawn to a bitmap because RemoteViews has no Canvas of its own, and sized from the real
 * widget size so it stays sharp when resized.
 */
class HeatmapWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.from(context)
        val summary = entryPoint.observePortfolio().invoke().first()

        provideContent {
            GlanceTheme {
                HeatmapContent(summary)
            }
        }
    }
}

class HeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapWidget()
}

@Composable
private fun HeatmapContent(summary: PortfolioSummary) {
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
        WidgetHeader(title = context.getString(R.string.widget_heatmap_label))

        val entries = summary.positions
            // A tile with no area cannot be drawn, and an unpriced position has no
            // meaningful colour either; both are already flagged inside the app.
            .filter { it.marketValueEur > 0.0 }
            .map {
                HeatmapEntry(
                    symbol = it.position.symbol,
                    weight = it.marketValueEur,
                    changePercent = if (it.isPriced) it.dayPnlPercent else 0.0,
                )
            }

        if (entries.isEmpty()) {
            EmptyMessage(context.getString(R.string.widget_empty_portfolio))
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

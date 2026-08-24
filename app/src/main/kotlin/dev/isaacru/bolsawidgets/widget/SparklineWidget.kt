package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.CandleSeries
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.theme.Gain
import dev.isaacru.bolsawidgets.ui.theme.Loss
import dev.isaacru.bolsawidgets.ui.theme.Neutral
import dev.isaacru.bolsawidgets.widget.render.BitmapBudget
import dev.isaacru.bolsawidgets.widget.render.SparklineRenderer
import java.time.Duration

/**
 * One configurable ticker with a sparkline.
 *
 * The symbol and the range are per-instance state, chosen in [SparklineConfigActivity]
 * when the widget is placed. Candles come from the Room cache first, so the chart keeps
 * its shape with no network; the series is only refetched once it is older than the
 * [cacheMaxAge] for its range.
 */
class SparklineWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Exact rather than Responsive: the bitmap has to be drawn at the real size, and
    // rounding it to a bucket would either blur it or waste pixels.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val symbol = preferences[KEY_SYMBOL]?.takeIf { it.isNotBlank() }
        val range = preferences[KEY_RANGE]
            ?.let { stored -> ChartRange.entries.firstOrNull { it.name == stored } }
            ?: ChartRange.DAY

        val entryPoint = WidgetEntryPoint.from(context)
        val quotes = entryPoint.quoteRepository()
        val quote = symbol?.let { quotes.getCachedQuotes(listOf(it))[it.uppercase()] }
        val series = symbol?.let { quotes.getCandleSeries(it, range, cacheMaxAge(range)) }

        provideContent {
            GlanceTheme {
                SparklineContent(symbol = symbol, range = range, quote = quote, series = series)
            }
        }
    }

    companion object {
        val KEY_SYMBOL = stringPreferencesKey("sparkline_symbol")
        val KEY_RANGE = stringPreferencesKey("sparkline_range")

        /**
         * How stale a series may get before it is refetched. A one-day chart moves every
         * few minutes; a one-year chart does not change meaningfully within a day.
         */
        fun cacheMaxAge(range: ChartRange): Duration = when (range) {
            ChartRange.DAY -> Duration.ofMinutes(15)
            ChartRange.WEEK -> Duration.ofHours(1)
            ChartRange.MONTH -> Duration.ofHours(6)
            ChartRange.YEAR -> Duration.ofHours(24)
        }
    }
}

class SparklineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SparklineWidget()
}

@Composable
private fun SparklineContent(
    symbol: String?,
    range: ChartRange,
    quote: Quote?,
    series: CandleSeries?,
) {
    val context = LocalContext.current
    val size = LocalSize.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity(symbolIntent(context, symbol.orEmpty()))),
    ) {
        if (symbol == null) {
            EmptyMessage(context.getString(R.string.widget_sparkline_unconfigured))
            return@Column
        }

        WidgetHeader(title = symbol, trailing = range.label)

        val change = quote?.changePercent ?: 0.0
        Text(
            text = quote?.let { Format.price(it.price, it.currency) }
                ?: context.getString(R.string.widget_no_price),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (size.width >= WidgetSizes.Wide.width) 22.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = changeArrow(change) + " " + Format.percent(change),
                style = TextStyle(
                    color = changeColorProvider(change),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }

        val candles = series?.candles.orEmpty()
        if (candles.size < 2) {
            Text(
                text = context.getString(R.string.widget_sparkline_no_series),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
            )
            return@Column
        }

        // Whatever vertical space the text above did not take. Kept above a floor so a
        // 2x2 still gets a drawable strip instead of a one-pixel smear.
        val chartHeightDp = (size.height.value - HEADER_AND_PRICE_DP).coerceAtLeast(24f)
        val chartWidthDp = (size.width.value - HORIZONTAL_PADDING_DP).coerceAtLeast(24f)
        val density = context.resources.displayMetrics.density
        val bitmap = SparklineRenderer.render(
            candles = candles,
            size = BitmapBudget.sizeFor(chartWidthDp, chartHeightDp, density),
            lineColor = when {
                change > 0.0 -> Gain.toArgb()
                change < 0.0 -> Loss.toArgb()
                else -> Neutral.toArgb()
            },
            baseline = quote?.previousClose?.takeIf { range == ChartRange.DAY && it > 0.0 },
        )

        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 4.dp),
        )
    }
}

private const val HEADER_AND_PRICE_DP = 68f
private const val HORIZONTAL_PADDING_DP = 20f

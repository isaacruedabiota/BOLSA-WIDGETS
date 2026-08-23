package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.ui.common.Format
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Watchlist widget: one row per followed symbol with its price and daily change.
 *
 * Data is read as a snapshot when the Glance session starts. [WidgetUpdater] is what
 * brings it back when Room changes, either from the background worker or from the app.
 */
class WatchlistWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(WidgetSizes.Wide, WidgetSizes.Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.from(context)
        val zone = entryPoint.zoneId()
        val rows = entryPoint.observeWatchlist().invoke().first()

        provideContent {
            GlanceTheme {
                WatchlistContent(rows = rows, zone = zone)
            }
        }
    }
}

class WatchlistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchlistWidget()
}

@Composable
private fun WatchlistContent(rows: List<WatchlistRow>, zone: ZoneId) {
    val compact = LocalSize.current.height < WidgetSizes.Tall.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        WidgetHeader(title = LocalContext.current.getString(R.string.widget_watchlist_label))

        if (rows.isEmpty()) {
            EmptyMessage(LocalContext.current.getString(R.string.widget_empty_watchlist))
            return@Column
        }

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(rows, itemId = { it.symbol.hashCode().toLong() }) { row ->
                WatchlistRowItem(row = row, zone = zone, compact = compact)
            }
        }
    }
}

@Composable
private fun WatchlistRowItem(row: WatchlistRow, zone: ZoneId, compact: Boolean) {
    val context = LocalContext.current
    val quote = row.quote

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(symbolIntent(context, row.symbol))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.symbol,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    text = row.displayName,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }

        if (quote == null) {
            Text(
                text = context.getString(R.string.widget_no_price),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.price(quote.price, quote.currency),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = changeArrow(quote.changePercent) + " " + Format.percent(quote.changePercent),
                    style = TextStyle(
                        color = changeColorProvider(quote.changePercent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Title on the left, manual refresh on the right. Shared by both text widgets. */
@Composable
internal fun WidgetHeader(title: String, trailing: String? = null) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = context.getString(R.string.widget_refresh),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .size(18.dp)
                .clickable(actionRunCallback<RefreshWidgetsAction>()),
        )
    }
}

@Composable
internal fun EmptyMessage(text: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

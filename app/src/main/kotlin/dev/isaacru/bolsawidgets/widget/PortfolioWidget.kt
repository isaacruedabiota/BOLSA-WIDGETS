package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.ui.common.Format
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Portfolio summary widget: total value in EUR plus the daily and total P&L.
 *
 * Honours the privacy switch from Ajustes, which is the whole point of having it: on a
 * home screen the absolute figure is the part someone else can read over your shoulder.
 */
class PortfolioWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(WidgetSizes.Small, WidgetSizes.Wide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.from(context)
        val zone = entryPoint.zoneId()
        val summary = entryPoint.observePortfolio().invoke().first()
        val privacyMode = entryPoint.settingsRepository().current().privacyMode

        provideContent {
            GlanceTheme {
                PortfolioContent(summary = summary, privacyMode = privacyMode, zone = zone)
            }
        }
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()
}

@Composable
private fun PortfolioContent(summary: PortfolioSummary, privacyMode: Boolean, zone: ZoneId) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WidgetSizes.Wide.width

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity(openPortfolioIntent(context))),
    ) {
        WidgetHeader(title = context.getString(R.string.widget_portfolio_label))

        if (summary.isEmpty) {
            EmptyMessage(context.getString(R.string.widget_empty_portfolio))
            return@Column
        }

        Text(
            text = amountText(context, summary.totalValueEur, privacyMode),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (wide) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )

        PnlLine(
            label = context.getString(R.string.widget_day),
            amountEur = summary.dayPnlEur,
            percent = summary.dayPnlPercent,
            privacyMode = privacyMode,
            wide = wide,
        )
        PnlLine(
            label = context.getString(R.string.widget_total),
            amountEur = summary.totalPnlEur,
            percent = summary.totalPnlPercent,
            privacyMode = privacyMode,
            wide = wide,
        )

        Text(
            text = lastUpdateText(context, summary.lastQuoteAt, zone),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PnlLine(
    label: String,
    amountEur: Double,
    percent: Double,
    privacyMode: Boolean,
    wide: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        // Narrow layouts drop the euro figure rather than truncating it: the percentage
        // is the part that still means something at 2x2.
        val body = if (privacyMode || !wide) {
            changeArrow(percent) + " " + Format.percent(percent)
        } else {
            Format.signedMoney(amountEur) + "  " + changeArrow(percent) + " " + Format.percent(percent)
        }
        Text(
            text = body,
            style = TextStyle(
                color = changeColorProvider(percent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
        )
    }
}

private fun amountText(context: Context, amountEur: Double, privacyMode: Boolean): String =
    if (privacyMode) context.getString(R.string.value_hidden) else Format.money(amountEur)

private fun lastUpdateText(context: Context, at: Instant?, zone: ZoneId): String =
    if (at == null) {
        context.getString(R.string.widget_never)
    } else {
        context.getString(R.string.widget_updated, Format.dateTime(at, zone))
    }

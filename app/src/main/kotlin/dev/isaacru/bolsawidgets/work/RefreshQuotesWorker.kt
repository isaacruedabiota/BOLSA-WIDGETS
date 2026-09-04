package dev.isaacru.bolsawidgets.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.isaacru.bolsawidgets.domain.market.MarketClock
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.usecase.RefreshMarketDataUseCase
import dev.isaacru.bolsawidgets.widget.HeatmapWidgetReceiver
import dev.isaacru.bolsawidgets.widget.SparklineWidget
import dev.isaacru.bolsawidgets.widget.SparklineWidgetReceiver
import dev.isaacru.bolsawidgets.widget.WatchlistWidgetReceiver
import java.time.Duration

/**
 * The periodic background refresh.
 *
 * Everything downstream is cache-first, which is why this worker never has to report
 * failure: if the network is gone, the previous values and their timestamps simply stay
 * in Room and the widgets keep showing them.
 *
 * It also does not redraw anything itself. The observer in the Application is watching
 * Room and redraws when what the widgets print actually changes, so a redraw here would
 * be a second RemoteViews round trip for the same data.
 */
@HiltWorker
class RefreshQuotesWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshMarketData: RefreshMarketDataUseCase,
    private val quoteRepository: QuoteRepository,
    private val marketClock: MarketClock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The background refresh exists to keep the home screen current. With no widget
        // placed there is nothing to keep current, and the app refreshes what it needs
        // when it is opened, so the whole run is skipped before any disk or radio.
        if (!hasWidgets()) {
            Log.i(TAG, "No widgets placed, nothing to keep warm")
            return Result.success()
        }

        val outcome = runCatching {
            refreshMarketData(staleAfter = Duration.ZERO, respectMarketHours = true)
        }.getOrElse { error ->
            Log.w(TAG, "Background refresh failed outright", error)
            // Deliberately not Result.retry(): the repository already retries transient
            // failures with backoff, and the next period is minutes away. Waking the
            // radio again on WorkManager backoff would cost battery for nothing.
            return Result.success()
        }

        when {
            outcome.skippedMarketsClosed -> Log.i(TAG, "Markets closed, no network used")
            outcome.requested.isEmpty() -> Log.i(TAG, "Nothing to refresh")
            else -> Log.i(
                TAG,
                "Refreshed " + outcome.updated.size + "/" + outcome.requested.size + " symbols",
            )
        }

        if (!outcome.skippedMarketsClosed) refreshSparklineSeries()
        return Result.success()
    }

    /** True when at least one widget of any kind is on the home screen. */
    private fun hasWidgets(): Boolean {
        val manager = AppWidgetManager.getInstance(appContext) ?: return false
        return PROVIDERS.any { provider ->
            manager.getAppWidgetIds(ComponentName(appContext, provider)).isNotEmpty()
        }
    }

    /**
     * Keeps the chart of every placed sparkline fresh.
     *
     * This is the one place a candle series is fetched in the background, on the periodic
     * cadence and only while its own market is worth fetching. The widget itself draws
     * from the cache, so no redraw can ever pull a chart down the wire.
     */
    private suspend fun refreshSparklineSeries() {
        val glanceIds = runCatching {
            GlanceAppWidgetManager(appContext).getGlanceIds(SparklineWidget::class.java)
        }.getOrElse { emptyList() }

        val now = marketClock.now()
        var drew = false
        glanceIds.forEach { glanceId ->
            val preferences = runCatching {
                getAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId)
            }.getOrNull() ?: return@forEach

            val symbol = preferences[SparklineWidget.KEY_SYMBOL]?.takeIf { it.isNotBlank() }
                ?: return@forEach
            if (!marketClock.shouldFetch(marketClock.marketOf(symbol), now)) return@forEach

            val range = ChartRange.entries
                .firstOrNull { it.name == preferences[SparklineWidget.KEY_RANGE] }
                ?: ChartRange.DAY
            // The cache age is the series' own: a one-year chart does not move in a day,
            // so most runs find it fresh and spend nothing.
            runCatching { quoteRepository.getCandleSeries(symbol, range, range.cacheMaxAge) }
                .onFailure { Log.w(TAG, "Could not refresh the series of " + symbol, it) }
                .onSuccess { series ->
                    // A series that came back stamped after this run started is one that
                    // actually went to the network.
                    if (series != null && !series.fetchedAt.isBefore(now)) drew = true
                }
        }

        // The chart lives in its own table, which nothing observes, so this is the one
        // redraw the worker still owns. Only the sparklines, and only when a chart really
        // changed underneath them.
        if (drew) runCatching { SparklineWidget().updateAll(appContext) }
    }

    companion object {
        const val TAG = "RefreshQuotesWorker"

        private val PROVIDERS = listOf(
            WatchlistWidgetReceiver::class.java,
            SparklineWidgetReceiver::class.java,
            HeatmapWidgetReceiver::class.java,
        )
    }
}

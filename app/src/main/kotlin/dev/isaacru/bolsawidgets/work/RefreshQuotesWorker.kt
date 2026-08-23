package dev.isaacru.bolsawidgets.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.isaacru.bolsawidgets.domain.usecase.RefreshMarketDataUseCase
import dev.isaacru.bolsawidgets.widget.WidgetUpdater
import java.time.Duration

/**
 * The periodic background refresh.
 *
 * It asks for a full refresh but with market hours respected, so a run at three in the
 * morning returns without opening a socket. Everything downstream is cache-first, which
 * is why this worker never has to report failure: if the network is gone, the previous
 * values and their timestamps simply stay in Room and the widgets keep showing them.
 */
@HiltWorker
class RefreshQuotesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshMarketData: RefreshMarketDataUseCase,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
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
        // Only when something actually changed: a redraw with identical data is a
        // pointless RemoteViews round trip.
        if (outcome.updated.isNotEmpty()) {
            runCatching { widgetUpdater.updateAll() }
                .onFailure { Log.w(TAG, "Could not redraw the widgets", it) }
        }
        return Result.success()
    }

    companion object {
        const val TAG = "RefreshQuotesWorker"
    }
}

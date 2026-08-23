package dev.isaacru.bolsawidgets.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import java.time.Duration

/**
 * The refresh button every widget carries.
 *
 * Market hours are deliberately ignored here, exactly as with the button inside the app:
 * a tap is a deliberate request and must never be silently dropped. A failure is also
 * silent by design, because the widget keeps showing the cached values and their
 * timestamp rather than an error.
 */
class RefreshWidgetsAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = WidgetEntryPoint.from(context)
        runCatching {
            entryPoint.refreshMarketData().invoke(
                staleAfter = Duration.ZERO,
                respectMarketHours = false,
            )
        }.onFailure { error ->
            Log.w(TAG, "Widget refresh failed", error)
        }
        // Redrawn either way: on success to show the new prices, on failure so the
        // timestamp the user is looking at is the one actually stored.
        entryPoint.widgetUpdater().updateAll()
    }

    private companion object {
        const val TAG = "RefreshWidgetsAction"
    }
}

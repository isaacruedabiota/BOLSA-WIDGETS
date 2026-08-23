package dev.isaacru.bolsawidgets.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws every widget the user has placed.
 *
 * Widgets read a snapshot of Room when their session starts, so something has to tell
 * them the snapshot is stale. That is either the background worker after a successful
 * fetch, or the observer in the Application after the user edits data in the app.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun updateAll() {
        WatchlistWidget().updateAll(context)
        PortfolioWidget().updateAll(context)
    }
}

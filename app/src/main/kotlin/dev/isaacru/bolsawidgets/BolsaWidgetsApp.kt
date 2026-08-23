package dev.isaacru.bolsawidgets

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import dev.isaacru.bolsawidgets.widget.WidgetUpdater
import dev.isaacru.bolsawidgets.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BolsaWidgetsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var portfolioRepository: PortfolioRepository

    @Inject
    lateinit var watchlistRepository: WatchlistRepository

    @Inject
    lateinit var quoteRepository: QuoteRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Lives as long as the process; only used for the one-shot scheduling below. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager is initialised on demand so Hilt can build workers. The default
     * initializer is removed from the manifest for the same reason.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Re-asserted on every start so the job survives a reinstall or a settings change
        // that never made it through. Enqueueing the same request is a no-op.
        applicationScope.launch { refreshScheduler.scheduleFromSettings() }
        observeDataForWidgets()
    }

    /**
     * Keeps the widgets in step with edits made inside the app.
     *
     * The background worker redraws them after its own fetches, but adding a position or
     * removing a symbol has to show up straight away too. Watching Room here covers every
     * mutation path at once, instead of sprinkling redraw calls through the ViewModels
     * and coupling the ui layer to the widget one.
     *
     * Preferences are watched alongside the data because the privacy switch changes what
     * the widgets are allowed to print, and it would otherwise keep showing the amounts
     * until the next price came in.
     */
    @OptIn(FlowPreview::class)
    private fun observeDataForWidgets() {
        applicationScope.launch {
            combine(
                portfolioRepository.observePositions(),
                watchlistRepository.observeItems(),
                quoteRepository.observeQuotes(),
                settingsRepository.preferences,
            ) { _, _, _, _ -> Unit }
                // The first emission is just the current contents, which the widgets
                // already drew for themselves.
                .drop(1)
                .debounce(WIDGET_UPDATE_DEBOUNCE_MILLIS)
                .collect { widgetUpdater.updateAll() }
        }
    }

    private companion object {
        const val WIDGET_UPDATE_DEBOUNCE_MILLIS = 500L
    }
}

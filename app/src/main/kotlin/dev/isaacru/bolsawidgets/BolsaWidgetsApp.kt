package dev.isaacru.bolsawidgets

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
     * The single place the widgets get redrawn from.
     *
     * Watching Room here covers every mutation path at once — the app, the background
     * worker, a CSV restore — instead of sprinkling redraw calls around and coupling the
     * ui layer to the widget one. Preferences are watched alongside because the privacy
     * switch changes what the widgets are allowed to print.
     *
     * What is collected is a signature of exactly what the widgets put on screen, not the
     * data itself, so the many writes that change nothing visible cost nothing. Every
     * successful fetch rewrites its row with a new timestamp even when the price has not
     * moved; without this, each of those would redraw four widgets and redraw the heat
     * map's bitmap, on the periodic cadence, all day.
     */
    @OptIn(FlowPreview::class)
    private fun observeDataForWidgets() {
        applicationScope.launch {
            combine(
                watchlistRepository.observeItems(),
                quoteRepository.observeQuotes(),
                settingsRepository.preferences,
            ) { items, quotes, preferences ->
                items.joinToString("|") { item ->
                    val quote = quotes[item.symbol.uppercase()]
                    listOf(
                        item.symbol,
                        item.contribution?.monthlyEur?.toString().orEmpty(),
                        quote?.price?.toString().orEmpty(),
                        quote?.changePercent?.toString().orEmpty(),
                        quote?.currency.orEmpty(),
                    ).joinToString(",")
                } + "#" + preferences.privacyMode
            }
                // The first emission is just the current contents, which the widgets
                // already drew for themselves.
                .drop(1)
                .distinctUntilChanged()
                .debounce(WIDGET_UPDATE_DEBOUNCE_MILLIS)
                // One failed redraw must not take the collector down with it: this flow
                // is the only thing keeping the widgets current for the whole process.
                .collect { runCatching { widgetUpdater.updateAll() } }
        }
    }

    private companion object {
        /** Long enough to coalesce a burst of writes, short enough to feel immediate. */
        const val WIDGET_UPDATE_DEBOUNCE_MILLIS = 800L
    }
}

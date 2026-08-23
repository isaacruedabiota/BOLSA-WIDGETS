package dev.isaacru.bolsawidgets

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.isaacru.bolsawidgets.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BolsaWidgetsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

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
    }
}

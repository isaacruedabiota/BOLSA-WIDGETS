package dev.isaacru.bolsawidgets.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single periodic work request that keeps the cache warm.
 *
 * Scheduling is idempotent: it runs on every app start and again whenever the interval
 * changes in Ajustes, and WorkManager keeps the existing cadence when the request has
 * not actually changed.
 */
@Singleton
class RefreshScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun scheduleFromSettings() {
        schedule(settingsRepository.current().refreshIntervalMinutes)
    }

    fun schedule(intervalMinutes: Int) {
        // WorkManager silently clamps anything under 15 minutes; clamping here keeps the
        // scheduled interval and the one shown in Ajustes from drifting apart.
        val interval = intervalMinutes
            .coerceAtLeast(UserPreferences.MIN_REFRESH_MINUTES)
            .toLong()

        val request = PeriodicWorkRequestBuilder<RefreshQuotesWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    // The only constraint the spec asks for. Anything stricter would make
                    // the widgets go stale for reasons the user cannot see.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "bolsa-periodic-refresh"
        const val TAG = "bolsa-refresh"
    }
}

package dev.isaacru.bolsawidgets.domain.model

import dev.isaacru.bolsawidgets.domain.provider.ProviderId

/**
 * Everything the Ajustes screen controls. Defaults are what a fresh install uses.
 *
 * [privacyMode] hides absolute amounts everywhere (app and widgets), leaving only
 * percentages, so the portfolio value is not readable over your shoulder.
 */
data class UserPreferences(
    val providerId: ProviderId = ProviderId.YAHOO,
    val privacyMode: Boolean = false,
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_MINUTES,
) {
    companion object {
        /** WorkManager will not run a periodic job more often than this. */
        const val MIN_REFRESH_MINUTES = 15
        const val DEFAULT_REFRESH_MINUTES = MIN_REFRESH_MINUTES

        val REFRESH_OPTIONS = listOf(15, 30, 60, 240)
    }
}

package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/** User preferences, backed by DataStore. */
interface SettingsRepository {

    val preferences: Flow<UserPreferences>

    suspend fun current(): UserPreferences

    suspend fun setProvider(providerId: ProviderId)

    suspend fun setPrivacyMode(enabled: Boolean)

    suspend fun setRefreshIntervalMinutes(minutes: Int)
}

package dev.isaacru.bolsawidgets.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        // A corrupt or unreadable file falls back to defaults instead of crashing the app.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toUserPreferences() }

    override suspend fun current(): UserPreferences = preferences.first()

    override suspend fun setProvider(providerId: ProviderId) {
        dataStore.edit { it[KEY_PROVIDER] = providerId.name }
    }

    override suspend fun setPrivacyMode(enabled: Boolean) {
        dataStore.edit { it[KEY_PRIVACY] = enabled }
    }

    override suspend fun setRefreshIntervalMinutes(minutes: Int) {
        dataStore.edit {
            it[KEY_REFRESH_MINUTES] = minutes.coerceAtLeast(UserPreferences.MIN_REFRESH_MINUTES)
        }
    }

    private fun Preferences.toUserPreferences() = UserPreferences(
        providerId = this[KEY_PROVIDER]?.let { stored ->
            ProviderId.entries.firstOrNull { it.name == stored }
        } ?: ProviderId.YAHOO,
        privacyMode = this[KEY_PRIVACY] ?: false,
        refreshIntervalMinutes = (this[KEY_REFRESH_MINUTES] ?: UserPreferences.DEFAULT_REFRESH_MINUTES)
            .coerceAtLeast(UserPreferences.MIN_REFRESH_MINUTES),
    )

    private companion object {
        val KEY_PROVIDER = stringPreferencesKey("provider_id")
        val KEY_PRIVACY = booleanPreferencesKey("privacy_mode")
        val KEY_REFRESH_MINUTES = intPreferencesKey("refresh_interval_minutes")
    }
}

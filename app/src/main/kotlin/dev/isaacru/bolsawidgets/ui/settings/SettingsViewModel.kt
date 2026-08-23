package dev.isaacru.bolsawidgets.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.work.RefreshScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val refreshScheduler: RefreshScheduler,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.preferences
        .map { SettingsUiState(preferences = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SettingsUiState())

    fun setProvider(providerId: ProviderId) {
        viewModelScope.launch { settingsRepository.setProvider(providerId) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPrivacyMode(enabled) }
    }

    fun setRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setRefreshIntervalMinutes(minutes)
            // Rescheduled straight away: leaving the stored value and the running job out
            // of step is the kind of drift that only shows up days later.
            refreshScheduler.schedule(minutes)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

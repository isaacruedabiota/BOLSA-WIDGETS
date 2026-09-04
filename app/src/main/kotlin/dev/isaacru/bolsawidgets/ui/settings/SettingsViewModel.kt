package dev.isaacru.bolsawidgets.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.csv.CsvParseResult
import dev.isaacru.bolsawidgets.domain.csv.PortfolioCsv
import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.repository.BackupRepository
import dev.isaacru.bolsawidgets.domain.model.ThemeMode
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.ui.common.UiMessage
import dev.isaacru.bolsawidgets.work.RefreshScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isWorking: Boolean = false,
    /** Parsed file waiting for the user to confirm it should replace everything. */
    val pendingImport: CsvParseResult? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val refreshScheduler: RefreshScheduler,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val pendingImport = MutableStateFlow<CsvParseResult?>(null)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)

    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferences,
        working,
        pendingImport,
    ) { preferences, isWorking, pending ->
        SettingsUiState(preferences = preferences, isWorking = isWorking, pendingImport = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SettingsUiState())

    fun setProvider(providerId: ProviderId) {
        viewModelScope.launch { settingsRepository.setProvider(providerId) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
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

    /**
     * Builds the CSV and hands it to [write], which is where the document the user picked
     * actually lives. Keeping the file handle at the edge is what stops this ViewModel
     * from needing a Context.
     */
    fun export(write: suspend (String) -> Boolean) {
        viewModelScope.launch {
            working.value = true
            try {
                val csv = backupRepository.exportCsv()
                val parsed = PortfolioCsv.parse(csv).backup
                val written = runCatching { write(csv) }.getOrDefault(false)
                messageChannel.send(
                    if (written) {
                        UiMessage.BackupExported(parsed.positions.size, parsed.watchlist.size)
                    } else {
                        UiMessage.BackupExportFailed
                    },
                )
            } finally {
                working.value = false
            }
        }
    }

    /**
     * Reads and parses a file without touching the database. Nothing is replaced until
     * [confirmImport], because a restore wipes what is already there.
     */
    fun stageImport(read: suspend () -> String?) {
        viewModelScope.launch {
            working.value = true
            try {
                val text = runCatching { read() }.getOrNull()
                if (text == null) {
                    messageChannel.send(UiMessage.BackupImportFailed)
                    return@launch
                }
                val parsed = PortfolioCsv.parse(text)
                if (parsed.backup.isEmpty) {
                    messageChannel.send(UiMessage.BackupImportEmpty)
                    return@launch
                }
                pendingImport.value = parsed
            } finally {
                working.value = false
            }
        }
    }

    fun confirmImport() {
        val staged = pendingImport.value ?: return
        viewModelScope.launch {
            working.value = true
            try {
                backupRepository.restore(staged.backup)
                messageChannel.send(
                    UiMessage.BackupImported(
                        staged.backup.positions.size,
                        staged.backup.watchlist.size,
                    ),
                )
            } finally {
                pendingImport.value = null
                working.value = false
            }
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

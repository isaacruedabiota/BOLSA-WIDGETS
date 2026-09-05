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
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.share.WatchlistShare
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
    private val watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val shareText = Channel<String>(Channel.BUFFERED)

    /** Emits the text of the list once it is ready to hand to the share sheet. */
    val listToShare: Flow<String> = shareText.receiveAsFlow()

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

    /** Builds the shareable text of the current watchlist. */
    fun shareList() {
        viewModelScope.launch {
            val items = watchlistRepository.getItems()
            if (items.isEmpty()) {
                messageChannel.send(UiMessage.ShareListEmpty)
                return@launch
            }
            shareText.send(WatchlistShare.encode(items))
        }
    }

    /**
     * Adds the values of a shared list to the watchlist.
     *
     * Added, never replacing: a list from someone else is something you take values from,
     * and wiping your own to accept it would be a strange way to say thank you. Every
     * symbol is resolved first, exactly like adding one by hand, so a friend's typo cannot
     * put a row in the database that will never price.
     */
    fun importSharedList(text: String) {
        viewModelScope.launch {
            if (working.value) return@launch
            val shared = WatchlistShare.decode(text)
            if (shared.isEmpty()) {
                messageChannel.send(UiMessage.SharedImportEmpty)
                return@launch
            }

            working.value = true
            try {
                val existing = watchlistRepository.getItems()
                    .map { it.symbol.uppercase() }
                    .toMutableSet()
                var added = 0
                var skipped = 0
                shared.forEach { candidate ->
                    if (!existing.add(candidate.symbol)) {
                        skipped++
                        return@forEach
                    }
                    val quote = runCatching { quoteRepository.resolveSymbol(candidate.symbol) }
                        .getOrNull()
                    if (quote == null) {
                        skipped++
                        return@forEach
                    }
                    watchlistRepository.add(
                        symbol = quote.symbol,
                        // The name travelled with the list, so the friend's own wording is
                        // kept; falling back to whatever the market calls it.
                        name = candidate.name.ifBlank { quote.shortName ?: quote.symbol },
                    )
                    added++
                }
                messageChannel.send(UiMessage.SharedImported(added = added, skipped = skipped))
            } finally {
                working.value = false
            }
        }
    }

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

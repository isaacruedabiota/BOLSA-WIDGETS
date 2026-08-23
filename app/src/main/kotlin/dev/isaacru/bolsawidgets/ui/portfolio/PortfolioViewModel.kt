package dev.isaacru.bolsawidgets.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.domain.usecase.ObservePortfolioUseCase
import dev.isaacru.bolsawidgets.domain.usecase.RefreshMarketDataUseCase
import dev.isaacru.bolsawidgets.ui.common.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

data class PortfolioUiState(
    val summary: PortfolioSummary = PortfolioSummary.Empty,
    val lotsBySymbol: Map<String, List<Position>> = emptyMap(),
    val privacyMode: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val isEmpty: Boolean get() = summary.isEmpty
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    observePortfolio: ObservePortfolioUseCase,
    settingsRepository: SettingsRepository,
    private val portfolioRepository: PortfolioRepository,
    private val refreshMarketData: RefreshMarketDataUseCase,
    val zoneId: ZoneId,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)

    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<PortfolioUiState> = combine(
        observePortfolio(),
        portfolioRepository.observePositions(),
        settingsRepository.preferences,
        refreshing,
    ) { summary, positions, preferences, isRefreshing ->
        PortfolioUiState(
            summary = summary,
            lotsBySymbol = positions.groupBy { it.symbol.uppercase() },
            privacyMode = preferences.privacyMode,
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), PortfolioUiState())

    init {
        // Opening the app tops up anything stale without re-fetching what is still fresh.
        viewModelScope.launch { runRefresh(OPEN_STALENESS, announce = false) }
    }

    /** The manual refresh button. Always hits the network. */
    fun refresh() {
        viewModelScope.launch { runRefresh(Duration.ZERO, announce = true) }
    }

    fun deleteLot(id: Long) {
        viewModelScope.launch { portfolioRepository.delete(id) }
    }

    private suspend fun runRefresh(staleAfter: Duration, announce: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        try {
            val outcome = refreshMarketData(staleAfter)
            if (announce) messageChannel.send(UiMessage.of(outcome))
        } finally {
            refreshing.value = false
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L

        /** Quotes younger than this are good enough when the app comes to the foreground. */
        val OPEN_STALENESS: Duration = Duration.ofMinutes(5)
    }
}

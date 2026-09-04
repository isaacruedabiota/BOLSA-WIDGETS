package dev.isaacru.bolsawidgets.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import dev.isaacru.bolsawidgets.domain.usecase.ObserveWatchlistUseCase
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

data class WatchlistUiState(
    val rows: List<WatchlistRow> = emptyList(),
    val isRefreshing: Boolean = false,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    private val watchlistRepository: WatchlistRepository,
    private val refreshMarketData: RefreshMarketDataUseCase,
    val zoneId: ZoneId,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)

    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<WatchlistUiState> =
        combine(observeWatchlist(), refreshing) { rows, isRefreshing ->
            WatchlistUiState(rows = rows, isRefreshing = isRefreshing)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), WatchlistUiState())

    /**
     * Adds an already verified symbol. The picker resolves it through the quote endpoint
     * first, so anything reaching here is known to price.
     */
    fun add(quote: Quote) {
        viewModelScope.launch {
            val existing = watchlistRepository.getItems()
            if (existing.any { it.symbol.equals(quote.symbol, ignoreCase = true) }) {
                messageChannel.send(UiMessage.SymbolAlreadyPresent(quote.symbol))
                return@launch
            }
            watchlistRepository.add(quote.symbol, quote.shortName ?: quote.symbol)
        }
    }

    fun remove(symbol: String) {
        viewModelScope.launch {
            watchlistRepository.remove(symbol)
            messageChannel.send(UiMessage.SymbolRemoved(symbol))
        }
    }

    /** Saves what the user puts into [symbol] every week or month, or clears it. */
    fun setContribution(symbol: String, contribution: Contribution?) {
        viewModelScope.launch {
            watchlistRepository.setContribution(symbol, contribution)
        }
    }

    /** Stars or unstars a value; the Explorar tab lists the starred ones first. */
    fun toggleFavorite(symbol: String, favorite: Boolean) {
        viewModelScope.launch { watchlistRepository.setFavorite(symbol, favorite) }
    }

    fun moveUp(symbol: String) = move(symbol, -1)

    fun moveDown(symbol: String) = move(symbol, 1)

    fun refresh() {
        viewModelScope.launch {
            if (refreshing.value) return@launch
            refreshing.value = true
            try {
                messageChannel.send(UiMessage.of(refreshMarketData(Duration.ZERO)))
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun move(symbol: String, offset: Int) {
        viewModelScope.launch {
            val order = watchlistRepository.getItems().map { it.symbol }.toMutableList()
            val from = order.indexOfFirst { it.equals(symbol, ignoreCase = true) }
            val to = from + offset
            if (from < 0 || to !in order.indices) return@launch
            order.add(to, order.removeAt(from))
            watchlistRepository.reorder(order)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

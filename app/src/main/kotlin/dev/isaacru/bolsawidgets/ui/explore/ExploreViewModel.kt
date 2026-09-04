package dev.isaacru.bolsawidgets.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.MarketMovers
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.domain.repository.MoversRepository
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import dev.isaacru.bolsawidgets.domain.usecase.ObserveWatchlistUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

data class ExploreUiState(
    val favorites: List<WatchlistRow> = emptyList(),
    val movers: MarketMovers = MarketMovers(),
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The Explorar tab: the user's starred values, and the two ends of today's ranking.
 *
 * The ranking is fetched when the screen is opened and its cache has gone stale, or when
 * the user asks for it. Never in the background: this is a screen someone is looking at,
 * not something a widget depends on.
 */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    private val watchlistRepository: WatchlistRepository,
    private val moversRepository: MoversRepository,
    val zoneId: ZoneId,
) : ViewModel() {

    private val movers = MutableStateFlow(MarketMovers())
    private val loading = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    val uiState: StateFlow<ExploreUiState> =
        combine(observeWatchlist(), movers, loading, failed) { rows, ranking, isLoading, hasFailed ->
            ExploreUiState(
                favorites = rows.filter { it.item.isFavorite },
                movers = ranking,
                isLoading = isLoading,
                failed = hasFailed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), ExploreUiState())

    init {
        load(FRESH_ENOUGH)
    }

    /** The pull the user makes: goes to the network whatever the cache says. */
    fun refresh() = load(Duration.ZERO)

    fun toggleFavorite(symbol: String, favorite: Boolean) {
        viewModelScope.launch { watchlistRepository.setFavorite(symbol, favorite) }
    }

    private fun load(maxAge: Duration) {
        viewModelScope.launch {
            if (loading.value) return@launch
            loading.value = true
            val ranking = runCatching { moversRepository.getMovers(maxAge) }.getOrNull()
            // The repository already falls back to its cache, so a null here means it had
            // nothing at all: the only case worth telling the user about.
            failed.value = ranking == null || ranking.isEmpty
            if (ranking != null) movers.value = ranking
            loading.value = false
        }
    }

    private companion object {
        /**
         * Switching tabs must not cost a request. Fifteen minutes is the same cadence the
         * background refresh runs at, so the ranking is never more stale than the prices
         * next to it.
         */
        val FRESH_ENOUGH: Duration = Duration.ofMinutes(15)
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

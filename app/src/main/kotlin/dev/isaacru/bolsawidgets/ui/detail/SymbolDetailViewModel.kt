package dev.isaacru.bolsawidgets.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import dev.isaacru.bolsawidgets.ui.navigation.SymbolDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class SymbolDetailUiState(
    val symbol: String = "",
    val quote: Quote? = null,
    val range: ChartRange = ChartRange.DAY,
    val candles: List<Candle> = emptyList(),
    val seriesFetchedAt: Instant? = null,
    val isLoadingChart: Boolean = false,
    val chartUnavailable: Boolean = false,
    val privacyMode: Boolean = false,
    /** The name the user gave this value in Seguimiento, when they gave it one. */
    val customName: String? = null,
) {
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: quote?.shortName ?: symbol
}

/**
 * The expanded view of one symbol: its price and its chart.
 *
 * The chart is loaded imperatively per range because it is the one thing here that can
 * touch the network; everything else is observed from Room and therefore works offline.
 */
@HiltViewModel
class SymbolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
    val zoneId: ZoneId,
) : ViewModel() {

    private val symbol: String = savedStateHandle.toRoute<SymbolDetailRoute>().symbol.uppercase()

    // Starts as loading: init kicks off the first load, and claiming otherwise would
    // make the screen show an empty chart for a frame.
    private val chart = MutableStateFlow(ChartState(range = ChartRange.DAY, isLoading = true))

    val uiState: StateFlow<SymbolDetailUiState> = combine(
        quoteRepository.observeQuote(symbol),
        settingsRepository.preferences,
        watchlistRepository.observeItems(),
        chart,
    ) { quote, preferences, watchlist, chartState ->
        SymbolDetailUiState(
            symbol = symbol,
            quote = quote,
            range = chartState.range,
            candles = chartState.candles,
            seriesFetchedAt = chartState.fetchedAt,
            isLoadingChart = chartState.isLoading,
            chartUnavailable = chartState.unavailable,
            privacyMode = preferences.privacyMode,
            customName = watchlist
                .firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                ?.name,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT),
        SymbolDetailUiState(symbol = symbol),
    )

    init {
        loadChart(ChartRange.DAY, force = false)
    }

    fun selectRange(range: ChartRange) {
        if (chart.value.range == range && chart.value.candles.isNotEmpty()) return
        loadChart(range, force = false)
    }

    /** The manual refresh: re-reads the quote and forces a fresh series. */
    fun refresh() {
        viewModelScope.launch { quoteRepository.refreshQuotes(listOf(symbol)) }
        loadChart(chart.value.range, force = true)
    }

    private fun loadChart(range: ChartRange, force: Boolean) {
        viewModelScope.launch {
            chart.value = ChartState(range = range, isLoading = true)
            val series = runCatching {
                quoteRepository.getCandleSeries(
                    symbol = symbol,
                    range = range,
                    maxAge = if (force) Duration.ZERO else range.cacheMaxAge,
                )
            }.getOrNull()

            chart.value = ChartState(
                range = range,
                candles = series?.candles.orEmpty(),
                fetchedAt = series?.fetchedAt,
                isLoading = false,
                // Distinguishes "still loading" from "there is nothing to draw", so the
                // screen can say so instead of showing an empty box forever.
                unavailable = (series?.candles?.size ?: 0) < 2,
            )
        }
    }

    private data class ChartState(
        val range: ChartRange,
        val candles: List<Candle> = emptyList(),
        val fetchedAt: Instant? = null,
        val isLoading: Boolean = false,
        val unavailable: Boolean = false,
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

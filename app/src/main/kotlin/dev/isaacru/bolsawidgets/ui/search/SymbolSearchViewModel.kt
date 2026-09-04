package dev.isaacru.bolsawidgets.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.market.Market
import dev.isaacru.bolsawidgets.domain.search.SymbolFilter
import dev.isaacru.bolsawidgets.domain.search.SymbolFilters
import dev.isaacru.bolsawidgets.domain.search.SymbolKind
import dev.isaacru.bolsawidgets.domain.search.SymbolSuggestion
import dev.isaacru.bolsawidgets.domain.usecase.SearchSymbolsUseCase
import dev.isaacru.bolsawidgets.domain.usecase.SymbolSearchOutcome
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SymbolSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val exactMatch: Quote? = null,
    /** Already filtered: this is what the list draws. */
    val suggestions: List<SymbolSuggestion> = emptyList(),
    val suggestionsUnavailable: Boolean = false,
    val resolvingSymbol: String? = null,
    val failedSymbol: String? = null,
    val filter: SymbolFilter = SymbolFilter.None,
    val availableKinds: List<SymbolKind> = emptyList(),
    val availableMarkets: List<Market> = emptyList(),
    /** How many suggestions came back before the filter narrowed them. */
    val totalSuggestions: Int = 0,
) {
    val hasResults: Boolean get() = exactMatch != null || suggestions.isNotEmpty()

    /** Chips are only worth the space when there is more than one thing to choose. */
    val showFilters: Boolean get() = availableKinds.size > 1 || availableMarkets.size > 1

    val showEmptyState: Boolean
        get() = query.isNotBlank() && !isSearching && !hasResults && totalSuggestions == 0

    /** Results exist, the filter is what is hiding them: a different thing to say. */
    val showFilteredOutState: Boolean
        get() = !isSearching && totalSuggestions > 0 && suggestions.isEmpty() && exactMatch == null
}

/**
 * Drives the symbol picker shared by Seguimiento and the sparkline widget's setup.
 *
 * Nothing leaves this screen without a successful quote call: suggestions are resolved
 * before being handed back, so a symbol that reaches Room is always priceable.
 */
@HiltViewModel
class SymbolSearchViewModel @Inject constructor(
    private val searchSymbols: SearchSymbolsUseCase,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val resolution = MutableStateFlow(ResolutionState())
    private val filter = MutableStateFlow(SymbolFilter.None)

    private val chosenChannel = Channel<Quote>(Channel.BUFFERED)

    /** Emits once a suggestion has been verified through the quote endpoint. */
    val chosen: Flow<Quote> = chosenChannel.receiveAsFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults: StateFlow<SearchState> = query
        // Waiting for a pause in typing keeps one request per word, not per keystroke.
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            flow {
                if (text.isBlank()) {
                    emit(SearchState())
                    return@flow
                }
                emit(SearchState(isSearching = true))
                emit(SearchState(isSearching = false, outcome = searchSymbols(text)))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SearchState())

    val uiState: StateFlow<SymbolSearchUiState> =
        combine(query, searchResults, resolution, filter) { text, results, resolving, selected ->
            val all = results.outcome.suggestions
            // Pruned against the results in hand, so a chip never survives into a search
            // that has nothing behind it.
            val effective = SymbolFilters.prune(selected, all)
            SymbolSearchUiState(
                query = text,
                isSearching = results.isSearching,
                exactMatch = results.outcome.exactMatch,
                suggestions = SymbolFilters.apply(all, effective),
                suggestionsUnavailable = results.outcome.suggestionsUnavailable,
                resolvingSymbol = resolving.inFlight,
                failedSymbol = resolving.failed,
                filter = effective,
                availableKinds = SymbolFilters.kindsIn(all),
                availableMarkets = SymbolFilters.marketsIn(all),
                totalSuggestions = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SymbolSearchUiState())

    /**
     * Clears the picker. The ViewModel is scoped to the destination, not to the sheet, so
     * it outlives a dismissal and would otherwise reopen showing the previous search.
     */
    fun reset() {
        query.value = ""
        resolution.value = ResolutionState()
        filter.value = SymbolFilter.None
    }

    /** Tapping the selected chip again clears it, which is how a chip row is expected to work. */
    fun toggleKind(kind: SymbolKind) {
        filter.update { it.with(kind.takeIf { candidate -> candidate != it.kind }) }
    }

    fun toggleMarket(market: Market) {
        filter.update { it.with(market.takeIf { candidate -> candidate != it.market }) }
    }

    fun clearFilters() {
        filter.value = SymbolFilter.None
    }

    fun onQueryChange(value: String) {
        query.value = value
        resolution.update { it.copy(failed = null) }
    }

    /** Verifies [symbol] and, if it prices, emits it through [chosen]. */
    fun choose(symbol: String) {
        if (resolution.value.inFlight != null) return
        viewModelScope.launch {
            resolution.value = ResolutionState(inFlight = symbol)
            val quote = runCatching { quoteRepository.resolveSymbol(symbol) }.getOrNull()
            resolution.value = ResolutionState(failed = if (quote == null) symbol else null)
            if (quote != null) chosenChannel.send(quote)
        }
    }

    /** An already verified exact match skips the second round trip. */
    fun chooseVerified(quote: Quote) {
        viewModelScope.launch { chosenChannel.send(quote) }
    }

    private data class SearchState(
        val isSearching: Boolean = false,
        val outcome: SymbolSearchOutcome = SymbolSearchOutcome.Empty,
    )

    private data class ResolutionState(
        val inFlight: String? = null,
        val failed: String? = null,
    )

    private companion object {
        // Short enough that the list feels like it is following the typing, long
        // enough that a word costs one request rather than one per letter.
        const val DEBOUNCE_MILLIS = 220L
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

package dev.isaacru.bolsawidgets.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
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
    val suggestions: List<SymbolSuggestion> = emptyList(),
    val suggestionsUnavailable: Boolean = false,
    val resolvingSymbol: String? = null,
    val failedSymbol: String? = null,
) {
    val hasResults: Boolean get() = exactMatch != null || suggestions.isNotEmpty()

    val showEmptyState: Boolean
        get() = query.isNotBlank() && !isSearching && !hasResults
}

/**
 * Drives the symbol picker shared by the Cartera editor and the Seguimiento screen.
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
        combine(query, searchResults, resolution) { text, results, resolving ->
            SymbolSearchUiState(
                query = text,
                isSearching = results.isSearching,
                exactMatch = results.outcome.exactMatch,
                suggestions = results.outcome.suggestions,
                suggestionsUnavailable = results.outcome.suggestionsUnavailable,
                resolvingSymbol = resolving.inFlight,
                failedSymbol = resolving.failed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SymbolSearchUiState())

    /**
     * Clears the picker. The ViewModel is scoped to the destination, not to the sheet, so
     * it outlives a dismissal and would otherwise reopen showing the previous search.
     */
    fun reset() {
        query.value = ""
        resolution.value = ResolutionState()
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
        const val DEBOUNCE_MILLIS = 350L
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

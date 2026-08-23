package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.search.SymbolSearch
import dev.isaacru.bolsawidgets.domain.search.SymbolSuggestion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Resolves what the user typed into something addable.
 *
 * Two independent paths run in parallel:
 *  - the typed text is resolved as an exact ticker through the quote endpoint the whole
 *    app already depends on. This path is authoritative: whatever it returns is
 *    guaranteed priceable.
 *  - Yahoo's search endpoint contributes suggestions. It is a separate undocumented
 *    endpoint, so its failure is reported as [SymbolSearchOutcome.suggestionsUnavailable]
 *    and never propagates. Losing it degrades the UI to "type the exact ticker".
 */
class SearchSymbolsUseCase @Inject constructor(
    private val quoteRepository: QuoteRepository,
    private val symbolSearch: SymbolSearch,
) {
    suspend operator fun invoke(query: String): SymbolSearchOutcome {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return SymbolSearchOutcome.Empty

        return coroutineScope {
            val exact = async {
                if (trimmed.looksLikeTicker()) {
                    runCatching { quoteRepository.resolveSymbol(trimmed) }.getOrNull()
                } else {
                    null
                }
            }
            val suggestions = async { runCatching { symbolSearch.search(trimmed) } }

            val suggestionResult = suggestions.await()
            val resolved = exact.await()
            SymbolSearchOutcome(
                exactMatch = resolved,
                suggestions = suggestionResult.getOrDefault(emptyList())
                    .filterNot { it.symbol.equals(resolved?.symbol, ignoreCase = true) },
                suggestionsUnavailable = suggestionResult.isFailure,
            )
        }
    }

    /**
     * Cheap guard so free text like "banco santander" does not cost a quote request.
     * Tickers are short and made of letters, digits and the separators Yahoo uses.
     */
    private fun String.looksLikeTicker(): Boolean =
        length <= MAX_TICKER_LENGTH && all { it.isLetterOrDigit() || it in TICKER_PUNCTUATION }

    private companion object {
        const val MIN_QUERY_LENGTH = 1
        const val MAX_TICKER_LENGTH = 12
        const val TICKER_PUNCTUATION = ".-^=&"
    }
}

/**
 * [exactMatch] is a verified, priceable quote. [suggestions] still have to be resolved
 * before they can be added.
 */
data class SymbolSearchOutcome(
    val exactMatch: Quote?,
    val suggestions: List<SymbolSuggestion>,
    val suggestionsUnavailable: Boolean,
) {
    val isEmpty: Boolean get() = exactMatch == null && suggestions.isEmpty()

    companion object {
        val Empty = SymbolSearchOutcome(null, emptyList(), false)
    }
}

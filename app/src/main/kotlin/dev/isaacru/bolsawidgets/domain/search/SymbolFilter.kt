package dev.isaacru.bolsawidgets.domain.search

import dev.isaacru.bolsawidgets.domain.market.Market

/**
 * What the suggestion list is narrowed by. A null side means "everything".
 */
data class SymbolFilter(
    val kind: SymbolKind? = null,
    val market: Market? = null,
) {
    val isEmpty: Boolean get() = kind == null && market == null

    fun with(kind: SymbolKind?): SymbolFilter = copy(kind = kind)

    fun with(market: Market?): SymbolFilter = copy(market = market)

    companion object {
        val None = SymbolFilter()
    }
}

/**
 * Filtering of the suggestion list, kept pure so the search screen has no logic of its own.
 *
 * Only the two things known before a symbol is resolved can be filtered by, its kind and
 * its venue. The options offered are the ones actually present in the current results: a
 * chip that can only ever return nothing is worse than no chip, and Yahoo's answer to
 * "santander" has no ETFs in it to filter for.
 */
object SymbolFilters {

    fun apply(suggestions: List<SymbolSuggestion>, filter: SymbolFilter): List<SymbolSuggestion> {
        if (filter.isEmpty) return suggestions
        return suggestions.filter { suggestion ->
            (filter.kind == null || suggestion.kind == filter.kind) &&
                (filter.market == null || suggestion.market == filter.market)
        }
    }

    /** The kinds present in [suggestions], in the order the enum declares them. */
    fun kindsIn(suggestions: List<SymbolSuggestion>): List<SymbolKind> =
        SymbolKind.entries.filter { kind -> suggestions.any { it.kind == kind } }

    /** The venues present in [suggestions], in the order the enum declares them. */
    fun marketsIn(suggestions: List<SymbolSuggestion>): List<Market> =
        Market.entries.filter { market -> suggestions.any { it.market == market } }

    /**
     * Drops the parts of [filter] that the new [suggestions] cannot satisfy.
     *
     * Typing changes the results under the filter, and a selection left pointing at
     * something no longer on offer would show an empty list with no way to tell why. What
     * still makes sense is kept: narrowing to ETFs and then typing another fund name
     * should not silently widen the search again.
     */
    fun prune(filter: SymbolFilter, suggestions: List<SymbolSuggestion>): SymbolFilter {
        if (filter.isEmpty) return filter
        val kinds = kindsIn(suggestions)
        val markets = marketsIn(suggestions)
        return SymbolFilter(
            kind = filter.kind?.takeIf { it in kinds },
            market = filter.market?.takeIf { it in markets },
        )
    }
}

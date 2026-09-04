package dev.isaacru.bolsawidgets.domain.search

import dev.isaacru.bolsawidgets.domain.market.Market

/**
 * Best-effort symbol suggestions.
 *
 * This is a convenience on top of the ticker resolver, never a requirement: the Seguimiento
 * screen and the widget pickers must stay usable when it fails, so callers swallow its
 * errors.
 */
interface SymbolSearch {

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<SymbolSuggestion>

    companion object {
        /**
         * Deliberately generous: the list is filtered on the device, and filters with
         * only a handful of rows behind them are not worth showing.
         */
        const val DEFAULT_LIMIT = 25
    }
}

/**
 * What kind of instrument a suggestion is.
 *
 * Yahoo answers with its own vocabulary and it drifts, so it is mapped onto a closed set
 * here: an unknown string becomes [OTHER] instead of inventing a filter nobody asked for.
 */
enum class SymbolKind {
    EQUITY,
    ETF,
    FUND,
    INDEX,
    CRYPTO,
    CURRENCY,
    OTHER,
    ;

    companion object {
        fun of(raw: String?): SymbolKind = when (raw?.trim()?.uppercase()) {
            "EQUITY", "STOCK", "EQUITIES" -> EQUITY
            "ETF" -> ETF
            "MUTUALFUND", "FUND" -> FUND
            "INDEX" -> INDEX
            "CRYPTOCURRENCY", "CRYPTO" -> CRYPTO
            "CURRENCY" -> CURRENCY
            else -> OTHER
        }
    }
}

/**
 * One row of the suggestion list. Unverified until it is resolved through a quote call.
 *
 * [kind] and [market] are what the list can be filtered by, and they are the only two
 * things known about a symbol before resolving it: price, currency and day change all
 * arrive with the quote, one call per symbol, which is not something a search screen can
 * afford for a list.
 */
data class SymbolSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String,
    val kind: SymbolKind = SymbolKind.OTHER,
) {
    val market: Market get() = Market.of(symbol)
}

package dev.isaacru.bolsawidgets.domain.provider

import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote

/** Selectable market-data backends. Persisted by name in settings, so do not rename. */
enum class ProviderId(val displayName: String) {
    YAHOO("Yahoo Finance"),
    TWELVE_DATA("Twelve Data"),
}

/**
 * Source of market data.
 *
 * Implementations are treated as fragile: callers are expected to fall back to the Room
 * cache rather than surface an error, so every method here is allowed to throw
 * [QuoteProviderException].
 */
interface QuoteProvider {

    val id: ProviderId

    /** True when the provider is usable (e.g. it has the API key it needs). */
    suspend fun isConfigured(): Boolean = true

    suspend fun getQuote(symbol: String): Quote

    /**
     * Quotes for several symbols. Implementations batch when the backend supports it and
     * fan out otherwise. Symbols that fail are omitted from the result rather than
     * failing the whole call, so the returned list may be shorter than [symbols].
     */
    suspend fun getQuotes(symbols: List<String>): List<Quote>

    suspend fun getCandles(
        symbol: String,
        range: ChartRange,
        interval: CandleInterval = range.defaultInterval,
    ): List<Candle>

    /** Units of [to] per one unit of [from]. Returns 1.0 when both codes match. */
    suspend fun getFxRate(from: String, to: String): Double
}

/** Any failure reaching or understanding a market-data backend. */
class QuoteProviderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Market data as the rest of the app sees it.
 *
 * Reads always come from the Room cache, so every consumer keeps working offline.
 * Writes only ever happen on a successful fetch: a failed refresh leaves the previous
 * value in place rather than blanking it.
 */
interface QuoteRepository {

    /** Every cached quote, keyed by upper-case symbol. */
    fun observeQuotes(): Flow<Map<String, Quote>>

    fun observeQuote(symbol: String): Flow<Quote?>

    /** Converter built from the cached FX snapshot. */
    fun observeConverter(): Flow<CurrencyConverter>

    suspend fun getCachedQuotes(symbols: List<String>): Map<String, Quote>

    /** Fetches [symbols] from the active provider and caches whatever came back. */
    suspend fun refreshQuotes(symbols: List<String>): RefreshOutcome

    /**
     * Refreshes the rate of every currency in [currencies] against EUR.
     * Rates younger than [maxAge] are left alone.
     */
    suspend fun refreshFxRates(currencies: Set<String>, maxAge: java.time.Duration): RefreshOutcome

    suspend fun getCandles(
        symbol: String,
        range: ChartRange,
        interval: CandleInterval = range.defaultInterval,
    ): List<Candle>

    /**
     * Candles for a chart, cache-first.
     *
     * Returns the cached series untouched while it is younger than [maxAge]; otherwise
     * fetches and stores a new one. A failed fetch falls back to whatever is cached, so a
     * sparkline on the home screen keeps its shape offline instead of going blank.
     * Returns null only when there is neither cache nor network.
     */
    suspend fun getCandleSeries(
        symbol: String,
        range: ChartRange,
        maxAge: java.time.Duration,
    ): CandleSeries?

    /**
     * The day's change in percent for [symbols], in one request, best effort.
     *
     * This is the cheap half of a quote: enough to put a percentage next to a name, not
     * enough to store one. Symbols the provider says nothing about are simply absent from
     * the result, and a failure is an empty map rather than an exception, because this only
     * ever decorates a list that has to keep working without it.
     */
    suspend fun getDayChanges(symbols: List<String>): Map<String, Double>

    /**
     * Checks that [symbol] is a ticker the active provider can actually price, returning
     * the quote it resolved to. Returns null when the symbol does not exist.
     * Nothing is written to the watchlist or the portfolio without passing through here.
     */
    suspend fun resolveSymbol(symbol: String): Quote?
}

/** A cached candle series together with the moment it was stored. */
data class CandleSeries(
    val symbol: String,
    val range: ChartRange,
    val candles: List<Candle>,
    val fetchedAt: Instant,
) {
    val isEmpty: Boolean get() = candles.isEmpty()
}

/** What a refresh managed to do. Never an error: partial success is the normal case. */
data class RefreshOutcome(
    val requested: List<String>,
    val updated: List<String>,
    val failed: List<String>,
    val finishedAt: Instant,
    /** True when the run deliberately spent no network because every market was shut. */
    val skippedMarketsClosed: Boolean = false,
) {
    val didNothing: Boolean get() = updated.isEmpty()

    val isCompleteFailure: Boolean get() = requested.isNotEmpty() && updated.isEmpty()

    companion object {
        fun nothingToDo(at: Instant) = RefreshOutcome(emptyList(), emptyList(), emptyList(), at)

        fun marketsClosed(at: Instant) =
            RefreshOutcome(emptyList(), emptyList(), emptyList(), at, skippedMarketsClosed = true)
    }
}

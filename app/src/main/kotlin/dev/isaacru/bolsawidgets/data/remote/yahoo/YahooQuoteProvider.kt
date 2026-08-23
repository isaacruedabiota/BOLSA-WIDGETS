package dev.isaacru.bolsawidgets.data.remote.yahoo

import dev.isaacru.bolsawidgets.data.remote.HttpStatusException
import dev.isaacru.bolsawidgets.data.remote.retryWithBackoff
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.provider.QuoteProvider
import dev.isaacru.bolsawidgets.domain.provider.QuoteProviderException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default market-data source: the public Yahoo Finance chart endpoint.
 *
 * The endpoint serves one symbol per call, so [getQuotes] fans out with a small
 * concurrency cap instead of batching. A symbol that fails is dropped from the result
 * rather than failing the whole refresh, which lets the caller keep the cached value
 * for just that symbol.
 */
@Singleton
class YahooQuoteProvider @Inject constructor(
    private val api: YahooChartApi,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : QuoteProvider {

    override val id: ProviderId = ProviderId.YAHOO

    override suspend fun getQuote(symbol: String): Quote = withContext(io) {
        val result = fetch(symbol, range = "1d", interval = "5m")
        YahooMapper.toQuote(result, symbol, Instant.now(clock))
    }

    override suspend fun getQuotes(symbols: List<String>): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        return withContext(io) {
            coroutineScope {
                symbols.distinct()
                    .map { symbol ->
                        async { gate.withPermit { runCatching { getQuote(symbol) }.getOrNull() } }
                    }
                    .awaitAll()
                    .filterNotNull()
            }
        }
    }

    override suspend fun getCandles(
        symbol: String,
        range: ChartRange,
        interval: CandleInterval,
    ): List<Candle> = withContext(io) {
        val result = fetch(
            symbol = symbol,
            range = YahooMapper.rangeParam(range),
            interval = YahooMapper.intervalParam(interval),
        )
        YahooMapper.toCandles(result)
    }

    override suspend fun getFxRate(from: String, to: String): Double {
        if (from.equals(to, ignoreCase = true)) return 1.0
        return withContext(io) {
            val symbol = YahooMapper.fxSymbol(from, to)
            // A 5-day window keeps the call working over weekends and holidays.
            val result = fetch(symbol, range = "5d", interval = "1d")
            val quote = YahooMapper.toQuote(result, symbol, Instant.now(clock))
            if (quote.price <= 0.0) {
                throw QuoteProviderException("Yahoo returned a non-positive FX rate for " + symbol)
            }
            quote.price
        }
    }

    private suspend fun fetch(symbol: String, range: String, interval: String): YahooChartResult =
        retryWithBackoff {
            val response = api.chart(symbol = symbol, range = range, interval = interval)
            if (!response.isSuccessful) {
                throw HttpStatusException(
                    code = response.code(),
                    message = "Yahoo answered HTTP " + response.code() + " for " + symbol,
                )
            }
            val body = response.body()
                ?: throw QuoteProviderException("Yahoo returned an empty body for " + symbol)
            YahooMapper.requireResult(body, symbol)
        }

    private companion object {
        /** Keeps the fan-out polite enough that Yahoo does not start answering 429. */
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}

package dev.isaacru.bolsawidgets.data.remote.yahoo

import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.provider.QuoteProviderException
import java.time.Instant

/**
 * Translates Yahoo wire types into domain models.
 *
 * Kept free of Retrofit and Android types so the parsing rules can be unit tested
 * against pinned JSON fixtures.
 */
object YahooMapper {

    fun rangeParam(range: ChartRange): String = when (range) {
        ChartRange.DAY -> "1d"
        ChartRange.WEEK -> "5d"
        ChartRange.MONTH -> "1mo"
        ChartRange.YEAR -> "1y"
    }

    fun intervalParam(interval: CandleInterval): String = when (interval) {
        CandleInterval.MINUTE_1 -> "1m"
        CandleInterval.MINUTE_5 -> "5m"
        CandleInterval.MINUTE_15 -> "15m"
        CandleInterval.MINUTE_30 -> "30m"
        CandleInterval.HOUR_1 -> "1h"
        CandleInterval.DAY_1 -> "1d"
        CandleInterval.WEEK_1 -> "1wk"
    }

    /** Yahoo expresses an FX pair as the two ISO codes followed by "=X". */
    fun fxSymbol(from: String, to: String): String =
        from.uppercase() + to.uppercase() + "=X"

    /**
     * Reads the payload's single result, or throws with Yahoo's own error description
     * when the symbol is unknown.
     */
    fun requireResult(response: YahooChartResponse, symbol: String): YahooChartResult {
        val chart = response.chart
            ?: throw QuoteProviderException("Yahoo returned an empty payload for " + symbol)
        chart.error?.let { error ->
            throw QuoteProviderException(
                "Yahoo rejected " + symbol + ": " + (error.description ?: error.code ?: "unknown error"),
            )
        }
        return chart.result?.firstOrNull()
            ?: throw QuoteProviderException("Yahoo returned no result for " + symbol)
    }

    /**
     * Builds a [Quote] from a chart result.
     *
     * The price falls back to the last traded close when the meta block has none, and
     * the previous close falls back to Yahoo's chart-relative close (FX pairs, for
     * example, only carry the latter). [fetchedAt] stands in for the market timestamp
     * when Yahoo omits it.
     */
    fun toQuote(
        result: YahooChartResult,
        requestedSymbol: String,
        fetchedAt: Instant,
    ): Quote {
        val meta = result.meta
            ?: throw QuoteProviderException("Yahoo result for " + requestedSymbol + " has no meta block")

        val price = meta.regularMarketPrice
            ?: result.closes().lastOrNull()
            ?: throw QuoteProviderException("Yahoo result for " + requestedSymbol + " has no price")

        val currency = meta.currency?.takeIf { it.isNotBlank() }
            ?: throw QuoteProviderException("Yahoo result for " + requestedSymbol + " has no currency")

        return Quote(
            symbol = (meta.symbol ?: requestedSymbol).uppercase(),
            price = price,
            previousClose = meta.previousClose ?: meta.chartPreviousClose ?: price,
            currency = currency,
            timestamp = meta.regularMarketTime?.let(Instant::ofEpochSecond) ?: fetchedAt,
            shortName = meta.shortName ?: meta.longName,
            exchange = meta.fullExchangeName ?: meta.exchangeName,
        )
    }

    /**
     * Builds the candle series, dropping the gaps Yahoo leaves as nulls (auctions,
     * halts, holidays). Missing OHLC legs fall back to the close of their own bar.
     */
    fun toCandles(result: YahooChartResult): List<Candle> {
        val timestamps = result.timestamp ?: return emptyList()
        val series = result.indicators?.quote?.firstOrNull() ?: return emptyList()
        return timestamps.indices.mapNotNull { index ->
            val close = series.close?.getOrNull(index) ?: return@mapNotNull null
            Candle(
                timestamp = Instant.ofEpochSecond(timestamps[index]),
                open = series.open?.getOrNull(index) ?: close,
                high = series.high?.getOrNull(index) ?: close,
                low = series.low?.getOrNull(index) ?: close,
                close = close,
                volume = series.volume?.getOrNull(index) ?: 0L,
            )
        }
    }

    private fun YahooChartResult.closes(): List<Double> =
        indicators?.quote?.firstOrNull()?.close.orEmpty().filterNotNull()
}

package dev.isaacru.bolsawidgets.data.remote.yahoo

import dev.isaacru.bolsawidgets.domain.model.MarketMover
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Yahoo's saved screeners: the day's ranking in a single request.
 *
 * The predefined lists are the only screener path still open without a crumb — the POST
 * screener and the batch quote endpoint both answer 401 — and their universe is the US
 * market. That is a limitation of the source, not a choice: there is no European ranking
 * to be had for one request.
 */
interface YahooScreenerApi {

    @GET("v1/finance/screener/predefined/saved")
    suspend fun screener(
        @Query("scrIds") screenId: String,
        @Query("count") count: Int,
        @Query("lang") lang: String = "es-ES",
        @Query("region") region: String = "ES",
    ): Response<YahooScreenerResponse>

    companion object {
        const val DAY_GAINERS = "day_gainers"
        const val DAY_LOSERS = "day_losers"
    }
}

@Serializable
data class YahooScreenerResponse(
    val finance: YahooScreenerFinance? = null,
)

@Serializable
data class YahooScreenerFinance(
    val result: List<YahooScreenerResult>? = null,
)

@Serializable
data class YahooScreenerResult(
    val quotes: List<YahooScreenerQuote>? = null,
)

@Serializable
data class YahooScreenerQuote(
    val symbol: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val fullExchangeName: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketChangePercent: Double? = null,
)

/**
 * Null price or ticker means a row that cannot be drawn or opened, so it is dropped
 * rather than shown half empty.
 */
fun YahooScreenerQuote.toDomain(): MarketMover? {
    val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
    val price = regularMarketPrice ?: return null
    return MarketMover(
        symbol = ticker.uppercase(),
        name = shortName ?: longName ?: ticker,
        exchange = fullExchangeName ?: exchange.orEmpty(),
        price = price,
        currency = currency.orEmpty().ifBlank { "USD" },
        changePercent = regularMarketChangePercent ?: 0.0,
    )
}

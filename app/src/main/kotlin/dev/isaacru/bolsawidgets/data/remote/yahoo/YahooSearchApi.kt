package dev.isaacru.bolsawidgets.data.remote.yahoo

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Yahoo's symbol lookup. Separate from the chart endpoint on purpose: it is a
 * convenience, and the app has to keep working when it is unavailable.
 */
interface YahooSearchApi {

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int,
        @Query("newsCount") newsCount: Int = 0,
        @Query("listsCount") listsCount: Int = 0,
        @Query("enableFuzzyQuery") enableFuzzyQuery: Boolean = false,
    ): Response<YahooSearchResponse>
}

@Serializable
data class YahooSearchResponse(
    val quotes: List<YahooSearchQuote>? = null,
)

@Serializable
data class YahooSearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val exchDisp: String? = null,
    val exchange: String? = null,
    val typeDisp: String? = null,
    val quoteType: String? = null,
    val isYahooFinance: Boolean? = null,
)

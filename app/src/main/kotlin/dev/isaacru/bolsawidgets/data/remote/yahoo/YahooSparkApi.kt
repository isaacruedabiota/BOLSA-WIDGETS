package dev.isaacru.bolsawidgets.data.remote.yahoo

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The one Yahoo endpoint that answers about **several symbols at once** without a crumb.
 *
 * The chart endpoint serves one symbol per call and the batch quote endpoint answers 401,
 * which is why the rest of the app fans out. This one exists for the mini charts on
 * Yahoo's own pages: it returns the day's series per symbol plus the change already
 * computed, and it is what makes showing a percentage next to every search result cost one
 * request instead of one per row.
 *
 * What it does not return is the currency or the name, so it can only ever add a
 * percentage to something already identified. The full quote still comes from the chart
 * endpoint, which is what keeps "nothing enters Room unpriced" true.
 */
interface YahooSparkApi {

    @GET("v8/finance/spark")
    suspend fun spark(
        @Query("symbols") symbols: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "5m",
    ): Response<Map<String, YahooSparkQuote>>

    companion object {
        /** Yahoo starts trimming the answer well before this; a search page never needs more. */
        const val MAX_SYMBOLS = 25
    }
}

@Serializable
data class YahooSparkQuote(
    val symbol: String? = null,
    val fulldayChangePercent: Double? = null,
    val chartPreviousClose: Double? = null,
    val previousClose: Double? = null,
    val close: List<Double?>? = null,
) {
    /**
     * The day's move in percent.
     *
     * Yahoo's own figure is used when it is there; otherwise it is computed from the last
     * traded price against the previous close, which is the same definition the rest of the
     * app uses. Null when neither is possible, because a percentage that is really "no
     * idea" must not be drawn as a flat zero.
     */
    fun changePercent(): Double? {
        fulldayChangePercent?.takeIf { it.isFinite() }?.let { return it }
        val reference = (chartPreviousClose ?: previousClose)?.takeIf { it > 0.0 } ?: return null
        val last = close?.lastOrNull { it != null && it.isFinite() } ?: return null
        return (last - reference) / reference * 100.0
    }
}

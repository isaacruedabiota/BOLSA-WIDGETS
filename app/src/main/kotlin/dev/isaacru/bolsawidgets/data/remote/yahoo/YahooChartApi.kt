package dev.isaacru.bolsawidgets.data.remote.yahoo

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The single Yahoo endpoint this app uses. It covers quotes, candles and FX pairs,
 * needs no API key, and works for BME (.MC), US listings and European ETFs.
 */
interface YahooChartApi {

    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String,
        @Query("includePrePost") includePrePost: Boolean = false,
    ): Response<YahooChartResponse>

    companion object {
        const val BASE_URL = "https://query1.finance.yahoo.com/"

        /**
         * Yahoo answers 429 to requests without a browser User-Agent, so the OkHttp
         * interceptor always sends one.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

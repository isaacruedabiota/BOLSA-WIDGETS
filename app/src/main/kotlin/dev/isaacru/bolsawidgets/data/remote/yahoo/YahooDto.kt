package dev.isaacru.bolsawidgets.data.remote.yahoo

import kotlinx.serialization.Serializable

/**
 * Wire types for the public Yahoo Finance chart endpoint.
 *
 * Every field is nullable on purpose: the endpoint is undocumented and its payload
 * varies by instrument type (an FX pair, for instance, carries no previousClose).
 */
@Serializable
data class YahooChartResponse(
    val chart: YahooChart? = null,
)

@Serializable
data class YahooChart(
    val result: List<YahooChartResult>? = null,
    val error: YahooError? = null,
)

@Serializable
data class YahooError(
    val code: String? = null,
    val description: String? = null,
)

@Serializable
data class YahooChartResult(
    val meta: YahooMeta? = null,
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null,
)

@Serializable
data class YahooMeta(
    val symbol: String? = null,
    val currency: String? = null,
    val exchangeName: String? = null,
    val fullExchangeName: String? = null,
    val instrumentType: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val chartPreviousClose: Double? = null,
    /** Epoch seconds of the last trade. */
    val regularMarketTime: Long? = null,
    val exchangeTimezoneName: String? = null,
    val gmtoffset: Long? = null,
)

@Serializable
data class YahooIndicators(
    val quote: List<YahooQuoteSeries>? = null,
)

@Serializable
data class YahooQuoteSeries(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null,
    val volume: List<Long?>? = null,
)

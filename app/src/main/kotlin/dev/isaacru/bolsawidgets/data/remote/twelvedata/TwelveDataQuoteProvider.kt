package dev.isaacru.bolsawidgets.data.remote.twelvedata

import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.provider.QuoteProvider
import dev.isaacru.bolsawidgets.domain.provider.QuoteProviderException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in second data source, selectable from Ajustes if Yahoo ever stops answering.
 *
 * Twelve Data covers BME (.MC), US listings and European ETFs on its free tier and has a
 * dedicated FX endpoint, but it needs an API key. Nothing is implemented yet: every call
 * fails loudly so the repository falls back to the Room cache exactly as it would for a
 * network error. Wiring the real calls is a later change and only touches this class.
 */
@Singleton
class TwelveDataQuoteProvider @Inject constructor() : QuoteProvider {

    override val id: ProviderId = ProviderId.TWELVE_DATA

    /** No API key is stored yet, so the provider is never usable. */
    override suspend fun isConfigured(): Boolean = false

    override suspend fun getQuote(symbol: String): Quote = notImplemented()

    override suspend fun getQuotes(symbols: List<String>): List<Quote> = notImplemented()

    override suspend fun getCandles(
        symbol: String,
        range: ChartRange,
        interval: CandleInterval,
    ): List<Candle> = notImplemented()

    override suspend fun getFxRate(from: String, to: String): Double = notImplemented()

    private fun notImplemented(): Nothing =
        throw QuoteProviderException("El proveedor Twelve Data aun no esta implementado")

    companion object {
        const val BASE_URL = "https://api.twelvedata.com/"
    }
}

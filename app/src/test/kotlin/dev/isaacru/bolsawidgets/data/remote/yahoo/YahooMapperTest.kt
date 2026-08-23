package dev.isaacru.bolsawidgets.data.remote.yahoo

import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.provider.QuoteProviderException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Parses pinned copies of real Yahoo payloads. The fixtures were captured from the live
 * endpoint and trimmed; the SAN.MC one has a deliberate null bar so the gap handling
 * stays covered.
 */
class YahooMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val fetchedAt: Instant = Instant.parse("2026-08-23T10:00:00Z")

    @Test
    fun `parses an equity quote from the Madrid exchange`() {
        val result = YahooMapper.requireResult(decode("san_mc_1d_5m.json"), "SAN.MC")

        val quote = YahooMapper.toQuote(result, "SAN.MC", fetchedAt)

        assertEquals("SAN.MC", quote.symbol)
        assertEquals(12.55, quote.price, DELTA)
        assertEquals(12.226, quote.previousClose, DELTA)
        assertEquals("EUR", quote.currency)
        assertEquals("BANCO SANTANDER S.A.", quote.shortName)
        assertEquals("MCE", quote.exchange)
        assertEquals(Instant.ofEpochSecond(1787326579), quote.timestamp)
    }

    @Test
    fun `derives the daily change from the previous close`() {
        val result = YahooMapper.requireResult(decode("san_mc_1d_5m.json"), "SAN.MC")

        val quote = YahooMapper.toQuote(result, "SAN.MC", fetchedAt)

        assertEquals(0.324, quote.change, DELTA)
        assertEquals(2.650089, quote.changePercent, 1e-5)
    }

    @Test
    fun `drops the bars Yahoo reports as null`() {
        val result = YahooMapper.requireResult(decode("san_mc_1d_5m.json"), "SAN.MC")

        val candles = YahooMapper.toCandles(result)

        // The fixture carries six timestamps and one null bar at index three.
        assertEquals(5, candles.size)
        assertEquals(Instant.ofEpochSecond(1787295600), candles.first().timestamp)
        assertEquals(12.376000, candles.first().close, 1e-5)
        assertTrue(candles.none { it.timestamp == Instant.ofEpochSecond(1787296500) })
        assertEquals(Instant.ofEpochSecond(1787297100), candles.last().timestamp)
    }

    @Test
    fun `falls back to the chart previous close for FX pairs`() {
        // Currency payloads carry chartPreviousClose but no previousClose.
        val result = YahooMapper.requireResult(decode("eurusd_5d_1d.json"), "EURUSD=X")

        val quote = YahooMapper.toQuote(result, "EURUSD=X", fetchedAt)

        assertEquals("EURUSD=X", quote.symbol)
        assertEquals(1.1678, quote.price, DELTA)
        assertEquals(1.1574, quote.previousClose, DELTA)
        assertEquals("USD", quote.currency)
    }

    @Test
    fun `surfaces the Yahoo error description for an unknown symbol`() {
        val response = decode("not_found.json")

        val error = assertThrows(QuoteProviderException::class.java) {
            YahooMapper.requireResult(response, "ZZZZNOTREAL.MC")
        }

        assertTrue(error.message.orEmpty().contains("symbol may be delisted"))
        assertNull(response.chart?.result)
    }

    @Test
    fun `maps ranges and intervals to Yahoo query parameters`() {
        assertEquals("1d", YahooMapper.rangeParam(ChartRange.DAY))
        assertEquals("5d", YahooMapper.rangeParam(ChartRange.WEEK))
        assertEquals("1mo", YahooMapper.rangeParam(ChartRange.MONTH))
        assertEquals("1y", YahooMapper.rangeParam(ChartRange.YEAR))

        assertEquals("5m", YahooMapper.intervalParam(CandleInterval.MINUTE_5))
        assertEquals("30m", YahooMapper.intervalParam(CandleInterval.MINUTE_30))
        assertEquals("1d", YahooMapper.intervalParam(CandleInterval.DAY_1))
        assertEquals("1wk", YahooMapper.intervalParam(CandleInterval.WEEK_1))
    }

    @Test
    fun `builds the Yahoo symbol for a currency pair`() {
        assertEquals("USDEUR=X", YahooMapper.fxSymbol("usd", "eur"))
        assertEquals("EURUSD=X", YahooMapper.fxSymbol("EUR", "USD"))
    }

    @Test
    fun `each chart range picks a readable default interval`() {
        assertEquals(CandleInterval.MINUTE_5, ChartRange.DAY.defaultInterval)
        assertEquals(CandleInterval.MINUTE_30, ChartRange.WEEK.defaultInterval)
        assertEquals(CandleInterval.DAY_1, ChartRange.MONTH.defaultInterval)
        assertEquals(CandleInterval.DAY_1, ChartRange.YEAR.defaultInterval)
    }

    private fun decode(fixture: String): YahooChartResponse =
        json.decodeFromString(YahooChartResponse.serializer(), readFixture(fixture))

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/yahoo/$name")) { "Missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val DELTA = 1e-6
    }
}

package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.FxRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CurrencyConverterTest {

    private val converter = CurrencyConverter(
        mapOf(
            "USDEUR" to 0.90,
            "EURGBP" to 0.85,
            "GBPEUR" to 1.15,
        ),
    )

    @Test
    fun `converting a currency into itself is the identity`() {
        assertEquals(100.0, converter.convert(100.0, "EUR", "EUR")!!, DELTA)
        assertEquals(1.0, converter.rate("USD", "USD")!!, DELTA)
    }

    @Test
    fun `uses a directly quoted pair`() {
        assertEquals(90.0, converter.convert(100.0, "USD", "EUR")!!, DELTA)
        assertEquals(90.0, converter.toEur(100.0, "USD")!!, DELTA)
    }

    @Test
    fun `inverts a pair quoted the other way round`() {
        // Only USDEUR is stored, so EUR to USD has to come from its inverse.
        assertEquals(100.0, converter.convert(90.0, "EUR", "USD")!!, DELTA)
    }

    @Test
    fun `triangulates an unquoted pair through EUR`() {
        // USD to GBP is not stored: 100 USD -> 90 EUR -> 76.5 GBP.
        assertEquals(76.5, converter.convert(100.0, "USD", "GBP")!!, DELTA)
    }

    @Test
    fun `treats GBp as pence rather than pounds`() {
        // A London listing quoted at 500 pence is 5 GBP, so 5.75 EUR at 1.15.
        assertEquals(5.75, converter.toEur(500.0, "GBp")!!, DELTA)
    }

    @Test
    fun `returns null when the snapshot cannot express the pair`() {
        assertNull(converter.convert(100.0, "JPY", "EUR"))
        assertNull(converter.rate("JPY", "USD"))
        assertNull(CurrencyConverter.Empty.toEur(10.0, "USD"))
    }

    @Test
    fun `ignores a non positive stored rate`() {
        val broken = CurrencyConverter(mapOf("USDEUR" to 0.0))

        assertNull(broken.toEur(100.0, "USD"))
    }

    @Test
    fun `is case insensitive on currency codes`() {
        assertEquals(90.0, converter.convert(100.0, "usd", "eur")!!, DELTA)
    }

    @Test
    fun `can be built from cached FX rows`() {
        val fromCache = CurrencyConverter(
            listOf(FxRate("USDEUR", 0.80, Instant.EPOCH)),
        )

        assertEquals(80.0, fromCache.toEur(100.0, "USD")!!, DELTA)
    }

    @Test
    fun `builds pair keys from two ISO codes`() {
        assertEquals("USDEUR", FxRate.pairOf("usd", "eur"))
    }

    private companion object {
        const val DELTA = 1e-9
    }
}

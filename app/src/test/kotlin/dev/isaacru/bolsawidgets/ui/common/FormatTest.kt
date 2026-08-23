package dev.isaacru.bolsawidgets.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Spanish formatting: comma as the decimal separator, dot for thousands, symbol after
 * the amount. Comparisons normalise the non-breaking space the JDK puts before the euro
 * sign, which varies between JDK builds.
 */
class FormatTest {

    @Test
    fun `formats euros the Spanish way`() {
        assertEquals("1.234,56 €", Format.money(1234.56).normalizeSpaces())
    }

    @Test
    fun `always signs a P and L amount`() {
        assertEquals("+45,00 €", Format.signedMoney(45.0).normalizeSpaces())
        assertEquals("-45,00 €", Format.signedMoney(-45.0).normalizeSpaces())
        assertEquals("+0,00 €", Format.signedMoney(0.0).normalizeSpaces())
    }

    @Test
    fun `signs percentages and keeps two decimals`() {
        assertEquals("+2,65 %", Format.percent(2.650089).normalizeSpaces())
        assertEquals("-1,40 %", Format.percent(-1.4).normalizeSpaces())
        assertEquals("7,09 %", Format.percent(7.090464, withSign = false).normalizeSpaces())
    }

    @Test
    fun `gives sub unit prices four decimals`() {
        // An FX pair at 1,1678 must not collapse to 1,17.
        assertEquals("1,1678", Format.plain(1.1678, 4))
        assertTrue(Format.price(0.8562, "USD").contains("0,8562"))
        assertTrue(Format.price(12.55, "EUR").contains("12,55"))
    }

    @Test
    fun `falls back to the raw code for minor unit currencies`() {
        // GBp is not an ISO currency, so it is printed as a plain suffix.
        assertEquals("500,00 GBp", Format.money(500.0, "GBp").normalizeSpaces())
    }

    @Test
    fun `drops trailing zeros from share counts`() {
        assertEquals("40", Format.quantity(40.0))
        assertEquals("2,5", Format.quantity(2.5))
    }

    @Test
    fun `formats purchase dates as day month year`() {
        assertEquals("15/01/2026", Format.date(LocalDate.of(2026, 1, 15)))
    }

    private fun String.normalizeSpaces(): String = replace(' ', ' ').replace(' ', ' ')
}

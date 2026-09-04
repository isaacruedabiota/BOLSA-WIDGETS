package dev.isaacru.bolsawidgets.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monogram stands in for a logo, so the only thing it owes anyone is being the same
 * every time: a circle that changed colour between screens would read as a different
 * company.
 */
class MonogramTest {

    @Test
    fun `the market suffix is not part of the initials`() {
        assertEquals("SA", Monogram.initialsOf("SAN.MC"))
        assertEquals("IW", Monogram.initialsOf("IWDA.AS"))
        assertEquals("BT", Monogram.initialsOf("BTC-EUR"))
        assertEquals("EU", Monogram.initialsOf("EURUSD=X"))
    }

    @Test
    fun `a one letter ticker keeps its one letter`() {
        assertEquals("F", Monogram.initialsOf("F"))
    }

    @Test
    fun `a ticker with nothing to show falls back rather than drawing an empty circle`() {
        assertEquals("-", Monogram.initialsOf("^"))
        assertEquals("-", Monogram.initialsOf(""))
    }

    @Test
    fun `the same symbol is always the same colour`() {
        assertEquals(Monogram.colorFor("SAN.MC"), Monogram.colorFor("SAN.MC"))
    }

    @Test
    fun `case and padding do not change the colour`() {
        assertEquals(Monogram.colorFor("san.mc"), Monogram.colorFor("  SAN.MC "))
    }

    @Test
    fun `two different symbols usually get different colours`() {
        // Not a guarantee with eight colours, but these two sit next to each other in the
        // user's own list, so a collision here would be visible every day.
        assertNotEquals(Monogram.colorFor("SAN.MC"), Monogram.colorFor("ITX.MC"))
    }

    @Test
    fun `every symbol lands inside the palette`() {
        val symbols = listOf("A", "SAN.MC", "IWDA.AS", "^IBEX", "BTC-EUR", "0P0001PUHM", "")
        val colors = symbols.map { Monogram.colorFor(it) }

        assertTrue(colors.all { it.alpha == 1f })
        assertTrue(Monogram.paletteSize > 1)
    }
}

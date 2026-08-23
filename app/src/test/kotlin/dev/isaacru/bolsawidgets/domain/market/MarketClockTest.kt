package dev.isaacru.bolsawidgets.domain.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every instant here is written as Madrid wall-clock time, because that is how the rules
 * were specified and how they will be checked by hand against a phone.
 */
class MarketClockTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = MarketClock(Clock.systemUTC())

    // 2026-08-25 is a Tuesday, well inside both summer times.
    private val summerTuesday = "2026-08-25"

    // 2026-03-17 is a Tuesday inside the DST mismatch: the United States moved on 8 March,
    // Europe does not move until 29 March.
    private val mismatchTuesday = "2026-03-17"

    @Test
    fun `maps Yahoo suffixes to their venue`() {
        assertEquals(Market.BME, Market.of("SAN.MC"))
        assertEquals(Market.US, Market.of("AAPL"))
        assertEquals(Market.EURONEXT, Market.of("IWDA.AS"))
        assertEquals(Market.XETRA, Market.of("VWCE.DE"))
        assertEquals(Market.BORSA_ITALIANA, Market.of("ENI.MI"))
        assertEquals(Market.LONDON, Market.of("VOD.L"))
    }

    @Test
    fun `treats instruments without an equity session as always open`() {
        assertEquals(Market.UNKNOWN, Market.of("^IBEX"))
        assertEquals(Market.UNKNOWN, Market.of("EURUSD=X"))
        assertEquals(Market.UNKNOWN, Market.of("BTC-EUR"))
        assertEquals(Market.UNKNOWN, Market.of("FOO.XX"))
        assertEquals(Market.UNKNOWN, Market.of(""))

        assertTrue(clock.isOpen("EURUSD=X", madridAt(summerTuesday, 3, 0)))
    }

    @Test
    fun `BME is open through its continuous session`() {
        assertTrue(clock.isOpen("SAN.MC", madridAt(summerTuesday, 9, 0)))
        assertTrue(clock.isOpen("SAN.MC", madridAt(summerTuesday, 13, 30)))
        assertTrue(clock.isOpen("SAN.MC", madridAt(summerTuesday, 17, 35)))
    }

    @Test
    fun `BME is shut before the open and after the closing auction`() {
        assertFalse(clock.isOpen("SAN.MC", madridAt(summerTuesday, 8, 59)))
        assertFalse(clock.isOpen("SAN.MC", madridAt(summerTuesday, 17, 36)))
        assertFalse(clock.isOpen("SAN.MC", madridAt(summerTuesday, 23, 0)))
    }

    @Test
    fun `nothing is open at the weekend`() {
        // 2026-08-29 is a Saturday, 2026-08-30 a Sunday.
        assertFalse(clock.isOpen("SAN.MC", madridAt("2026-08-29", 12, 0)))
        assertFalse(clock.isOpen("AAPL", madridAt("2026-08-30", 18, 0)))
        assertFalse(clock.shouldFetch(listOf("SAN.MC", "AAPL"), madridAt("2026-08-29", 12, 0)))
    }

    @Test
    fun `US hours land at 15 30 Madrid time in the usual case`() {
        assertFalse(clock.isOpen("AAPL", madridAt(summerTuesday, 15, 29)))
        assertTrue(clock.isOpen("AAPL", madridAt(summerTuesday, 15, 30)))
        assertTrue(clock.isOpen("AAPL", madridAt(summerTuesday, 22, 0)))
        assertFalse(clock.isOpen("AAPL", madridAt(summerTuesday, 22, 1)))
    }

    @Test
    fun `US hours shift an hour earlier while the two DST changes disagree`() {
        // New York is already on summer time and Madrid is not, so the session opens at
        // 14:30 Madrid. Declaring the window in New York time is what makes this work.
        assertTrue(clock.isOpen("AAPL", madridAt(mismatchTuesday, 14, 30)))
        assertFalse(clock.isOpen("AAPL", madridAt(mismatchTuesday, 14, 29)))
        // The same wall-clock time in August is still an hour before the open.
        assertFalse(clock.isOpen("AAPL", madridAt(summerTuesday, 14, 30)))
    }

    @Test
    fun `one more fetch is allowed after the close so the closing price is captured`() {
        val justClosed = madridAt(summerTuesday, 17, 40)

        assertFalse(clock.isOpen("SAN.MC", justClosed))
        assertTrue(clock.shouldFetch(listOf("SAN.MC"), justClosed))
        assertFalse(clock.shouldFetch(listOf("SAN.MC"), madridAt(summerTuesday, 17, 56)))
    }

    @Test
    fun `a single open market is enough to justify the run`() {
        // 18:00 Madrid: Spain has closed and its grace window is over, Wall Street is open.
        val evening = madridAt(summerTuesday, 18, 0)

        assertFalse(clock.shouldFetch(listOf("SAN.MC"), evening))
        assertTrue(clock.shouldFetch(listOf("SAN.MC", "AAPL"), evening))
    }

    @Test
    fun `an empty portfolio is never worth a request`() {
        assertFalse(clock.shouldFetch(emptyList(), madridAt(summerTuesday, 16, 0)))
    }

    @Test
    fun `nothing is fetched in the middle of the night`() {
        assertFalse(clock.shouldFetch(listOf("SAN.MC", "AAPL", "IWDA.AS"), madridAt(summerTuesday, 3, 0)))
    }

    @Test
    fun `reports the venues behind a set of symbols`() {
        assertEquals(
            setOf(Market.BME, Market.US, Market.EURONEXT),
            clock.marketsOf(listOf("SAN.MC", "ITX.MC", "AAPL", "IWDA.AS")),
        )
    }

    private fun madridAt(date: String, hour: Int, minute: Int): Instant =
        LocalDateTime.parse(date + "T" + pad(hour) + ":" + pad(minute) + ":00")
            .atZone(madrid)
            .toInstant()

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}

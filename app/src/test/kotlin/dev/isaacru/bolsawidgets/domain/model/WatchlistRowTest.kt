package dev.isaacru.bolsawidgets.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/**
 * One line of logic, but it is the line that decides what every screen and every widget
 * prints for a value, so renaming either works here or it does not work anywhere.
 */
class WatchlistRowTest {

    @Test
    fun `the name the user gave it wins over the market's`() {
        val row = row(name = "Mi ETF global", shortName = "iShares Core MSCI World UCITS ETF")

        assertEquals("Mi ETF global", row.displayName)
    }

    @Test
    fun `clearing the name is how a rename is undone, not how a row goes blank`() {
        val row = row(name = "", shortName = "iShares Core MSCI World UCITS ETF")

        assertEquals("iShares Core MSCI World UCITS ETF", row.displayName)
    }

    @Test
    fun `a value with no name anywhere falls back to its ticker`() {
        val row = row(name = "", shortName = null)

        assertEquals("IWDA.AS", row.displayName)
    }

    @Test
    fun `a renamed value keeps its name with no quote at all`() {
        // Airplane mode, or a symbol that has not been fetched yet: the widgets still have
        // something to print.
        val row = WatchlistRow(
            item = WatchlistItem("IWDA.AS", "Mi ETF global", 0),
            quote = null,
        )

        assertEquals("Mi ETF global", row.displayName)
    }

    private fun row(name: String, shortName: String?) = WatchlistRow(
        item = WatchlistItem("IWDA.AS", name, 0),
        quote = Quote(
            symbol = "IWDA.AS",
            price = 127.44,
            previousClose = 127.88,
            currency = "EUR",
            shortName = shortName,
            timestamp = Instant.EPOCH,
        ),
    )

    @Test
    fun `the name stored when the value was added is not a rename`() {
        // Adding a value keeps the provider's name, so this must not count as the user
        // having renamed anything: otherwise every row on the home screen would lead with
        // its long market name instead of its ticker.
        val row = row(name = "iShares Core MSCI World UCITS ETF", shortName = "iShares Core MSCI World UCITS ETF")

        assertFalse(row.hasCustomName)
    }

    @Test
    fun `a name of the user's own counts as one`() {
        val row = row(name = "Mi ETF global", shortName = "iShares Core MSCI World UCITS ETF")

        assertTrue(row.hasCustomName)
    }

    @Test
    fun `with no quote to compare against, a stored name stands on its own`() {
        // Airplane mode on a value never fetched: printing the stored name beats printing
        // nothing, and it is the only name there is.
        val row = WatchlistRow(item = WatchlistItem("IWDA.AS", "Mi ETF global", 0), quote = null)

        assertTrue(row.hasCustomName)
    }
}

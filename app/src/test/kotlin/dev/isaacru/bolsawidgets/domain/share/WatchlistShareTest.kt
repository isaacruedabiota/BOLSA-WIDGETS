package dev.isaacru.bolsawidgets.domain.share

import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.ContributionPeriod
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shared list is text that has been through a chat app, so the parser is the whole
 * feature: it has to survive greetings, quoted replies and someone typing the list by
 * hand, and it must never carry out what it was not asked to share.
 */
class WatchlistShareTest {

    private val items = listOf(
        WatchlistItem("IWDA.AS", "Mi ETF global", 0),
        WatchlistItem("SAN.MC", "BANCO SANTANDER S.A.", 1),
    )

    @Test
    fun `a shared list survives the round trip`() {
        val decoded = WatchlistShare.decode(WatchlistShare.encode(items))

        assertEquals(
            listOf(
                SharedSymbol("IWDA.AS", "Mi ETF global"),
                SharedSymbol("SAN.MC", "BANCO SANTANDER S.A."),
            ),
            decoded,
        )
    }

    @Test
    fun `what you invest never leaves the phone`() {
        val withMoney = listOf(
            WatchlistItem(
                symbol = "IWDA.AS",
                name = "Mi ETF global",
                sortOrder = 0,
                contribution = Contribution(200.0, ContributionPeriod.MONTHLY),
                isFavorite = true,
            ),
        )

        val text = WatchlistShare.encode(withMoney)

        assertFalse(text.contains("200"))
        assertFalse(text.contains("MONTHLY", ignoreCase = true))
        assertTrue(text.contains("IWDA.AS"))
    }

    @Test
    fun `the message around the list is ignored`() {
        val whatsapp = """
            Buenas! esta es mi lista

            Bolsa Widgets · lista de seguimiento
            IWDA.AS | Mi ETF global
            SAN.MC | Banco Santander

            dime que te parece
        """.trimIndent()

        val decoded = WatchlistShare.decode(whatsapp)

        assertEquals(listOf("IWDA.AS", "SAN.MC"), decoded.map { it.symbol })
    }

    @Test
    fun `a quoted reply is still readable`() {
        val quoted = "> IWDA.AS | Mi ETF global\n> SAN.MC"

        assertEquals(listOf("IWDA.AS", "SAN.MC"), WatchlistShare.decode(quoted).map { it.symbol })
    }

    @Test
    fun `a list typed by hand with commas works too`() {
        val typed = "ITX.MC, Inditex\nTEF.MC\nibe.mc"

        val decoded = WatchlistShare.decode(typed)

        assertEquals(listOf("ITX.MC", "TEF.MC", "IBE.MC"), decoded.map { it.symbol })
        assertEquals("Inditex", decoded.first().name)
    }

    @Test
    fun `the same value twice is one value`() {
        val decoded = WatchlistShare.decode("SAN.MC\nSAN.MC | Santander\nsan.mc")

        assertEquals(listOf("SAN.MC"), decoded.map { it.symbol })
        // The first line wins, so the order of the list is the order it was sent in.
        assertEquals("", decoded.single().name)
    }

    @Test
    fun `prose is not a ticker`() {
        val decoded = WatchlistShare.decode("Hola, mira mi lista\nque te parece?\n2026")

        // "2026" has no letters but is a plausible ticker shape; the sentences are not.
        assertTrue(decoded.none { it.symbol.contains(' ') })
        assertFalse(decoded.any { it.symbol.startsWith("HOLA") })
    }

    @Test
    fun `an empty list produces a header and nothing else`() {
        val decoded = WatchlistShare.decode(WatchlistShare.encode(emptyList()))

        assertTrue(decoded.isEmpty())
    }
}

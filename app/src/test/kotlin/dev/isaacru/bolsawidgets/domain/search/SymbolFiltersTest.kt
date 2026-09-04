package dev.isaacru.bolsawidgets.domain.search

import dev.isaacru.bolsawidgets.domain.market.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search screen has no logic of its own, so this is where the filtering is checked:
 * what a chip does, which chips are offered, and what happens to a selection when the
 * results change under it.
 */
class SymbolFiltersTest {

    private val santanderMadrid = suggestion("SAN.MC", "Banco Santander", SymbolKind.EQUITY)
    private val santanderNewYork = suggestion("SAN", "Banco Santander ADR", SymbolKind.EQUITY)
    private val inditex = suggestion("ITX.MC", "Industria de Diseno Textil", SymbolKind.EQUITY)
    private val worldEtf = suggestion("IWDA.AS", "iShares Core MSCI World", SymbolKind.ETF)
    private val ibex = suggestion("^IBEX", "IBEX 35", SymbolKind.INDEX)

    private val all = listOf(santanderMadrid, santanderNewYork, inditex, worldEtf, ibex)

    @Test
    fun `no filter is every result`() {
        assertEquals(all, SymbolFilters.apply(all, SymbolFilter.None))
    }

    @Test
    fun `filtering by kind keeps only that kind`() {
        val etfs = SymbolFilters.apply(all, SymbolFilter(kind = SymbolKind.ETF))

        assertEquals(listOf(worldEtf), etfs)
    }

    @Test
    fun `filtering by market keeps only that venue`() {
        val madrid = SymbolFilters.apply(all, SymbolFilter(market = Market.BME))

        assertEquals(listOf(santanderMadrid, inditex), madrid)
    }

    @Test
    fun `the two filters narrow together`() {
        val filter = SymbolFilter(kind = SymbolKind.EQUITY, market = Market.BME)

        assertEquals(listOf(santanderMadrid, inditex), SymbolFilters.apply(all, filter))
    }

    @Test
    fun `a bare ticker is a US listing and an index is neither`() {
        // Yahoo leaves US listings without a suffix, so this is what tells the two
        // Santanders apart without asking anything else.
        assertEquals(Market.US, santanderNewYork.market)
        assertEquals(Market.BME, santanderMadrid.market)
        assertEquals(Market.UNKNOWN, ibex.market)
    }

    @Test
    fun `only the options actually present are offered`() {
        val onlyEtfs = listOf(worldEtf)

        assertEquals(listOf(SymbolKind.ETF), SymbolFilters.kindsIn(onlyEtfs))
        assertEquals(listOf(Market.EURONEXT), SymbolFilters.marketsIn(onlyEtfs))
    }

    @Test
    fun `the options come back in a stable order rather than the order Yahoo answered in`() {
        val kinds = SymbolFilters.kindsIn(all.reversed())

        assertEquals(listOf(SymbolKind.EQUITY, SymbolKind.ETF, SymbolKind.INDEX), kinds)
    }

    @Test
    fun `a selection the new results cannot satisfy is dropped`() {
        // Narrowed to ETFs, then typed something that only matches shares: keeping the
        // chip would show an empty list with no explanation.
        val pruned = SymbolFilters.prune(
            SymbolFilter(kind = SymbolKind.ETF),
            listOf(santanderMadrid, inditex),
        )

        assertNull(pruned.kind)
        assertTrue(pruned.isEmpty)
    }

    @Test
    fun `a selection the new results still support survives`() {
        val pruned = SymbolFilters.prune(SymbolFilter(market = Market.BME), all)

        assertEquals(Market.BME, pruned.market)
    }

    @Test
    fun `one half of a filter can be dropped without taking the other with it`() {
        val pruned = SymbolFilters.prune(
            SymbolFilter(kind = SymbolKind.CRYPTO, market = Market.BME),
            all,
        )

        assertNull(pruned.kind)
        assertEquals(Market.BME, pruned.market)
    }

    @Test
    fun `Yahoo's vocabulary maps onto the closed set, and an unknown word is not invented`() {
        assertEquals(SymbolKind.EQUITY, SymbolKind.of("EQUITY"))
        assertEquals(SymbolKind.ETF, SymbolKind.of("etf"))
        assertEquals(SymbolKind.FUND, SymbolKind.of("MUTUALFUND"))
        assertEquals(SymbolKind.CRYPTO, SymbolKind.of("CRYPTOCURRENCY"))
        assertEquals(SymbolKind.OTHER, SymbolKind.of("FUTURE"))
        assertEquals(SymbolKind.OTHER, SymbolKind.of(null))
        assertEquals(SymbolKind.OTHER, SymbolKind.of(""))
    }

    private fun suggestion(symbol: String, name: String, kind: SymbolKind) = SymbolSuggestion(
        symbol = symbol,
        name = name,
        exchange = "TEST",
        type = kind.name,
        kind = kind,
    )
}

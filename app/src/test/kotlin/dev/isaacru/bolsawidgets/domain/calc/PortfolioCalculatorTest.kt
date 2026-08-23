package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PortfolioCalculatorTest {

    private val converter = CurrencyConverter(mapOf("USDEUR" to 0.90))

    @Test
    fun `folds several lots of a symbol with a quantity weighted average price`() {
        val lots = listOf(
            position(id = 1, symbol = "SAN.MC", quantity = 10.0, price = 4.00),
            position(id = 2, symbol = "SAN.MC", quantity = 30.0, price = 5.00),
        )

        val aggregated = PortfolioCalculator.aggregate(lots).single()

        assertEquals(40.0, aggregated.quantity, DELTA)
        // (10 * 4 + 30 * 5) / 40 = 4.75, not the plain average of 4.50.
        assertEquals(4.75, aggregated.averageBuyPrice, DELTA)
        assertEquals(190.0, aggregated.costBasis, DELTA)
        assertEquals(listOf(1L, 2L), aggregated.lotIds)
    }

    @Test
    fun `keeps symbols separate and in first appearance order`() {
        val lots = listOf(
            position(id = 1, symbol = "ITX.MC", quantity = 5.0, price = 40.0),
            position(id = 2, symbol = "AAPL", quantity = 2.0, price = 100.0, currency = "USD"),
            position(id = 3, symbol = "ITX.MC", quantity = 5.0, price = 50.0),
        )

        val aggregated = PortfolioCalculator.aggregate(lots)

        assertEquals(listOf("ITX.MC", "AAPL"), aggregated.map { it.symbol })
        assertEquals(45.0, aggregated.first().averageBuyPrice, DELTA)
    }

    @Test
    fun `matches lots of the same symbol regardless of case`() {
        val lots = listOf(
            position(id = 1, symbol = "aapl", quantity = 1.0, price = 100.0, currency = "USD"),
            position(id = 2, symbol = "AAPL", quantity = 1.0, price = 200.0, currency = "USD"),
        )

        val aggregated = PortfolioCalculator.aggregate(lots).single()

        assertEquals("AAPL", aggregated.symbol)
        assertEquals(150.0, aggregated.averageBuyPrice, DELTA)
    }

    @Test
    fun `a fully sold symbol collapses to a zero average instead of dividing by zero`() {
        val lots = listOf(
            position(id = 1, symbol = "REP.MC", quantity = 10.0, price = 12.0),
            position(id = 2, symbol = "REP.MC", quantity = -10.0, price = 15.0),
        )

        val aggregated = PortfolioCalculator.aggregate(lots).single()

        assertEquals(0.0, aggregated.quantity, DELTA)
        assertEquals(0.0, aggregated.averageBuyPrice, DELTA)
    }

    @Test
    fun `converts a foreign position into EUR for both PnL figures`() {
        val lots = listOf(position(id = 1, symbol = "AAPL", quantity = 10.0, price = 100.0, currency = "USD"))
        val quotes = mapOf("AAPL" to quote("AAPL", price = 110.0, previousClose = 105.0, currency = "USD"))

        val valuation = PortfolioCalculator
            .valueAll(PortfolioCalculator.aggregate(lots), quotes, converter)
            .single()

        assertTrue(valuation.isPriced)
        assertEquals(990.0, valuation.marketValueEur, DELTA)   // 10 * 110 USD * 0.90
        assertEquals(900.0, valuation.costBasisEur, DELTA)     // 10 * 100 USD * 0.90
        assertEquals(945.0, valuation.previousValueEur, DELTA) // 10 * 105 USD * 0.90
        assertEquals(45.0, valuation.dayPnlEur, DELTA)
        assertEquals(90.0, valuation.totalPnlEur, DELTA)
        assertEquals(4.761905, valuation.dayPnlPercent, 1e-5)
        assertEquals(10.0, valuation.totalPnlPercent, DELTA)
    }

    @Test
    fun `rolls positions up into portfolio totals`() {
        val lots = listOf(
            position(id = 1, symbol = "AAPL", quantity = 10.0, price = 100.0, currency = "USD"),
            position(id = 2, symbol = "SAN.MC", quantity = 100.0, price = 10.0),
        )
        val quotes = mapOf(
            "AAPL" to quote("AAPL", price = 110.0, previousClose = 105.0, currency = "USD"),
            "SAN.MC" to quote("SAN.MC", price = 12.0, previousClose = 11.0, currency = "EUR"),
        )

        val summary = PortfolioCalculator.summarize(lots, quotes, converter)

        assertEquals(2190.0, summary.totalValueEur, DELTA)
        assertEquals(1900.0, summary.costBasisEur, DELTA)
        assertEquals(2045.0, summary.previousValueEur, DELTA)
        assertEquals(145.0, summary.dayPnlEur, DELTA)
        assertEquals(290.0, summary.totalPnlEur, DELTA)
        assertEquals(7.090464, summary.dayPnlPercent, 1e-5)
        assertEquals(15.263158, summary.totalPnlPercent, 1e-5)
        assertTrue(summary.unpricedSymbols.isEmpty())
    }

    @Test
    fun `an unpriced symbol keeps its cost basis in the total and reports zero PnL`() {
        val lots = listOf(
            position(id = 1, symbol = "SAN.MC", quantity = 100.0, price = 10.0),
            position(id = 2, symbol = "NEW.MC", quantity = 5.0, price = 20.0),
        )
        val quotes = mapOf("SAN.MC" to quote("SAN.MC", price = 12.0, previousClose = 11.0, currency = "EUR"))

        val summary = PortfolioCalculator.summarize(lots, quotes, converter)

        assertEquals(listOf("NEW.MC"), summary.unpricedSymbols)
        assertEquals(1300.0, summary.totalValueEur, DELTA) // 1200 priced + 100 at cost
        assertEquals(100.0, summary.dayPnlEur, DELTA)      // the unpriced lot adds nothing
        assertEquals(200.0, summary.totalPnlEur, DELTA)
        assertFalse(summary.positions.first { it.position.symbol == "NEW.MC" }.isPriced)
    }

    @Test
    fun `a position in a currency with no FX rate counts as unpriced`() {
        val lots = listOf(position(id = 1, symbol = "7203.T", quantity = 10.0, price = 2000.0, currency = "JPY"))
        val quotes = mapOf("7203.T" to quote("7203.T", price = 2500.0, previousClose = 2400.0, currency = "JPY"))

        val summary = PortfolioCalculator.summarize(lots, quotes, converter)

        assertEquals(listOf("7203.T"), summary.unpricedSymbols)
        assertEquals(0.0, summary.dayPnlEur, DELTA)
    }

    @Test
    fun `reports the newest and the stalest quote timestamps`() {
        val lots = listOf(
            position(id = 1, symbol = "SAN.MC", quantity = 1.0, price = 10.0),
            position(id = 2, symbol = "ITX.MC", quantity = 1.0, price = 40.0),
        )
        val older = Instant.parse("2026-08-23T09:00:00Z")
        val newer = Instant.parse("2026-08-23T15:30:00Z")
        val quotes = mapOf(
            "SAN.MC" to quote("SAN.MC", 12.0, 11.0, "EUR", older),
            "ITX.MC" to quote("ITX.MC", 45.0, 44.0, "EUR", newer),
        )

        val summary = PortfolioCalculator.summarize(lots, quotes, converter)

        assertEquals(newer, summary.lastQuoteAt)
        assertEquals(older, summary.stalestQuoteAt)
    }

    @Test
    fun `an empty portfolio summarizes to zeroes`() {
        val summary = PortfolioCalculator.summarize(emptyList(), emptyMap(), converter)

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.totalValueEur, DELTA)
        assertEquals(0.0, summary.dayPnlPercent, DELTA)
    }

    @Test
    fun `position weight drives the heat map area`() {
        val lots = listOf(
            position(id = 1, symbol = "SAN.MC", quantity = 100.0, price = 10.0),
            position(id = 2, symbol = "ITX.MC", quantity = 10.0, price = 30.0),
        )
        val quotes = mapOf(
            "SAN.MC" to quote("SAN.MC", price = 12.0, previousClose = 11.0, currency = "EUR"),
            "ITX.MC" to quote("ITX.MC", price = 40.0, previousClose = 39.0, currency = "EUR"),
        )

        val summary = PortfolioCalculator.summarize(lots, quotes, converter)
        val santander = summary.positions.first { it.position.symbol == "SAN.MC" }

        assertEquals(1600.0, summary.totalValueEur, DELTA)
        assertEquals(0.75, santander.weightIn(summary.totalValueEur), DELTA)
    }

    private fun position(
        id: Long,
        symbol: String,
        quantity: Double,
        price: Double,
        currency: String = "EUR",
    ) = Position(
        id = id,
        symbol = symbol,
        name = symbol,
        exchange = "TEST",
        quantity = quantity,
        averageBuyPrice = price,
        currency = currency,
        purchaseDate = LocalDate.of(2026, 1, 15),
        notes = "",
    )

    private fun quote(
        symbol: String,
        price: Double,
        previousClose: Double,
        currency: String,
        timestamp: Instant = Instant.parse("2026-08-23T15:30:00Z"),
    ) = Quote(
        symbol = symbol,
        price = price,
        previousClose = previousClose,
        currency = currency,
        timestamp = timestamp,
    )

    private companion object {
        const val DELTA = 1e-6
    }
}

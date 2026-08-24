package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RepeatPurchaseTest {

    private val may = lot(id = 1, quantity = 0.5241, price = 95.40, date = LocalDate.of(2026, 5, 2))
    private val june = lot(id = 2, quantity = 0.5108, price = 97.88, date = LocalDate.of(2026, 6, 2))

    @Test
    fun `takes the most recent lot of the symbol`() {
        val positions = listOf(june, may, lot(id = 3, quantity = 1.0, price = 12.0, symbol = "SAN.MC"))

        assertEquals(june, RepeatPurchase.lastLot(positions, "IWDA.AS"))
    }

    @Test
    fun `breaks a same day tie by the newest row`() {
        val sameDay = june.copy(id = 9, averageBuyPrice = 99.0)

        val last = RepeatPurchase.lastLot(listOf(june, sameDay), "IWDA.AS")

        assertEquals(9L, last?.id)
    }

    @Test
    fun `finds the symbol regardless of case`() {
        assertEquals(june, RepeatPurchase.lastLot(listOf(may, june), "iwda.as"))
    }

    @Test
    fun `a symbol that is not held has no last lot`() {
        assertNull(RepeatPurchase.lastLot(listOf(june), "AAPL"))
    }

    @Test
    fun `the amount to repeat is what the last lot cost`() {
        // A savings plan buys a fixed amount of money, so this is the figure that repeats.
        assertEquals(50.0, RepeatPurchase.amountOf(june), 0.005)
    }

    @Test
    fun `quantity comes from the amount and the price`() {
        assertEquals(0.5, RepeatPurchase.quantityFor(amount = 50.0, price = 100.0)!!, 1e-9)
        assertEquals(2.0, RepeatPurchase.quantityFor(amount = 25.0, price = 12.5)!!, 1e-9)
    }

    @Test
    fun `an impossible purchase produces no quantity instead of a zero share lot`() {
        assertNull(RepeatPurchase.quantityFor(amount = 0.0, price = 100.0))
        assertNull(RepeatPurchase.quantityFor(amount = -50.0, price = 100.0))
        assertNull(RepeatPurchase.quantityFor(amount = 50.0, price = 0.0))
        assertNull(RepeatPurchase.quantityFor(amount = 50.0, price = -3.0))
        assertNull(RepeatPurchase.quantityFor(amount = Double.NaN, price = 100.0))
    }

    @Test
    fun `the new lot keeps the instrument and takes todays date`() {
        val today = LocalDate.of(2026, 7, 2)

        val draft = RepeatPurchase.draft(june, amount = 50.0, price = 101.0, on = today)!!

        assertEquals(0L, draft.id)
        assertEquals("IWDA.AS", draft.symbol)
        assertEquals(june.name, draft.name)
        assertEquals(june.exchange, draft.exchange)
        assertEquals("EUR", draft.currency)
        assertEquals(today, draft.purchaseDate)
        assertEquals(101.0, draft.averageBuyPrice, 1e-9)
        assertEquals(50.0 / 101.0, draft.quantity, 1e-9)
    }

    @Test
    fun `notes are not carried over because they described the old purchase`() {
        val withNote = june.copy(notes = "primera aportacion del plan")

        val draft = RepeatPurchase.draft(withNote, amount = 50.0, price = 101.0, on = LocalDate.of(2026, 7, 2))!!

        assertEquals("", draft.notes)
    }

    @Test
    fun `a bad amount produces no draft at all`() {
        assertNull(RepeatPurchase.draft(june, amount = 0.0, price = 101.0, on = LocalDate.of(2026, 7, 2)))
    }

    @Test
    fun `repeating three months averages out the way a savings plan should`() {
        // 50 EUR a month at three different prices: the weighted average must land
        // between them, not at the arithmetic mean of the prices.
        val lots = listOf(
            RepeatPurchase.draft(june, 50.0, 100.0, LocalDate.of(2026, 7, 2))!!.copy(id = 1),
            RepeatPurchase.draft(june, 50.0, 125.0, LocalDate.of(2026, 8, 2))!!.copy(id = 2),
            RepeatPurchase.draft(june, 50.0, 200.0, LocalDate.of(2026, 9, 2))!!.copy(id = 3),
        )

        val aggregated = PortfolioCalculator.aggregate(lots).single()

        // 150 EUR spent on 0.5 + 0.4 + 0.25 = 1.15 shares.
        assertEquals(1.15, aggregated.quantity, 1e-9)
        assertEquals(150.0 / 1.15, aggregated.averageBuyPrice, 1e-9)
        // Cost averaging: below the 141.67 mean of the three prices.
        assertEquals(130.43, aggregated.averageBuyPrice, 0.01)
    }

    private fun lot(
        id: Long,
        quantity: Double,
        price: Double,
        date: LocalDate = LocalDate.of(2026, 6, 2),
        symbol: String = "IWDA.AS",
    ) = Position(
        id = id,
        symbol = symbol,
        name = "iShares Core MSCI World",
        exchange = "AMS",
        quantity = quantity,
        averageBuyPrice = price,
        currency = "EUR",
        purchaseDate = date,
        notes = "",
    )
}

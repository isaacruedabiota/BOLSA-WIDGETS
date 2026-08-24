package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.Position
import java.time.LocalDate

/**
 * Repeating a monthly purchase.
 *
 * A savings plan buys a **fixed amount of money**, not a fixed number of shares: the
 * broker takes 50 € and hands back whatever fraction that buys at the fill price. So the
 * thing worth carrying over from last month is the amount, and the quantity is derived.
 *
 * Pure functions, no Android: the arithmetic that decides how many shares you own is the
 * last place to guess.
 */
object RepeatPurchase {

    /** The most recent lot of [symbol], or null when the symbol is not held. */
    fun lastLot(positions: List<Position>, symbol: String): Position? =
        positions
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
            .maxWithOrNull(compareBy({ it.purchaseDate }, { it.id }))

    /** What that lot cost, in the instrument's own currency. */
    fun amountOf(lot: Position): Double = lot.quantity * lot.averageBuyPrice

    /**
     * Shares bought with [amount] at [price]. Null when the inputs cannot describe a
     * purchase, so a bad entry never silently becomes a zero-share lot.
     */
    fun quantityFor(amount: Double, price: Double): Double? {
        if (amount <= 0.0 || price <= 0.0) return null
        if (!amount.isFinite() || !price.isFinite()) return null
        return amount / price
    }

    /**
     * A new lot of the same instrument, bought [on] for [amount] at [price]. Identity
     * fields are copied from [lot] because they belong to the instrument, not the trade;
     * the notes are not, because they described that particular purchase.
     */
    fun draft(lot: Position, amount: Double, price: Double, on: LocalDate): Position? {
        val quantity = quantityFor(amount, price) ?: return null
        return Position(
            id = 0L,
            symbol = lot.symbol,
            name = lot.name,
            exchange = lot.exchange,
            quantity = quantity,
            averageBuyPrice = price,
            currency = lot.currency,
            purchaseDate = on,
            notes = "",
        )
    }
}

package dev.isaacru.bolsawidgets.domain.model

import java.time.LocalDate

/**
 * A single manually entered buy lot.
 *
 * Several positions may share a [symbol]; they are folded into an
 * [AggregatedPosition] with a quantity-weighted average buy price before valuation.
 */
data class Position(
    val id: Long = 0L,
    val symbol: String,
    val name: String,
    val exchange: String,
    val quantity: Double,
    val averageBuyPrice: Double,
    val currency: String,
    val purchaseDate: LocalDate,
    val notes: String = "",
)

/** All lots of one symbol folded together. */
data class AggregatedPosition(
    val symbol: String,
    val name: String,
    val exchange: String,
    val quantity: Double,
    val averageBuyPrice: Double,
    val currency: String,
    val lotIds: List<Long>,
) {
    /** Cost basis in the position's own currency. */
    val costBasis: Double
        get() = quantity * averageBuyPrice
}

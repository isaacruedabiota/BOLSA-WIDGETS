package dev.isaacru.bolsawidgets.domain.model

import java.time.Instant

/**
 * One aggregated position valued in EUR.
 *
 * When [quote] is null the symbol has never been priced (not even from cache). Such a
 * position still contributes its cost basis to the portfolio value so the total is not
 * silently short, but it reports zero P&L and is listed in
 * [PortfolioSummary.unpricedSymbols] so the UI can flag it.
 */
data class PositionValuation(
    val position: AggregatedPosition,
    val quote: Quote?,
    val marketValueEur: Double,
    val costBasisEur: Double,
    val previousValueEur: Double,
    val dayPnlEur: Double,
    val totalPnlEur: Double,
) {
    val isPriced: Boolean get() = quote != null

    val dayPnlPercent: Double
        get() = if (previousValueEur == 0.0) 0.0 else (dayPnlEur / previousValueEur) * 100.0

    val totalPnlPercent: Double
        get() = if (costBasisEur == 0.0) 0.0 else (totalPnlEur / costBasisEur) * 100.0

    /** Share of the whole portfolio, 0..1. Drives the heat-map rectangle areas. */
    fun weightIn(totalValueEur: Double): Double =
        if (totalValueEur == 0.0) 0.0 else marketValueEur / totalValueEur
}

/** Portfolio-wide totals, always in EUR. */
data class PortfolioSummary(
    val totalValueEur: Double,
    val costBasisEur: Double,
    val previousValueEur: Double,
    val dayPnlEur: Double,
    val totalPnlEur: Double,
    val positions: List<PositionValuation>,
    val unpricedSymbols: List<String>,
    val lastQuoteAt: Instant?,
    val stalestQuoteAt: Instant?,
) {
    val dayPnlPercent: Double
        get() = if (previousValueEur == 0.0) 0.0 else (dayPnlEur / previousValueEur) * 100.0

    val totalPnlPercent: Double
        get() = if (costBasisEur == 0.0) 0.0 else (totalPnlEur / costBasisEur) * 100.0

    val isEmpty: Boolean get() = positions.isEmpty()

    companion object {
        val Empty = PortfolioSummary(
            totalValueEur = 0.0,
            costBasisEur = 0.0,
            previousValueEur = 0.0,
            dayPnlEur = 0.0,
            totalPnlEur = 0.0,
            positions = emptyList(),
            unpricedSymbols = emptyList(),
            lastQuoteAt = null,
            stalestQuoteAt = null,
        )
    }
}

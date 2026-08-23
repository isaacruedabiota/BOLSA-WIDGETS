package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.AggregatedPosition
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.PositionValuation
import dev.isaacru.bolsawidgets.domain.model.Quote

/**
 * Pure portfolio maths: lot aggregation, EUR valuation and P&L.
 *
 * Everything here is deterministic and free of Android/Room/network types so it can be
 * unit tested directly. Cost basis is converted with the *current* FX snapshot, matching
 * the app's rule that the whole portfolio is expressed in today's EUR.
 */
object PortfolioCalculator {

    /**
     * Folds every lot of the same symbol into one position with a quantity-weighted
     * average buy price. Symbol order follows first appearance in [positions].
     */
    fun aggregate(positions: List<Position>): List<AggregatedPosition> =
        positions
            .groupBy { it.symbol.uppercase() }
            .map { (symbol, lots) -> foldLots(symbol, lots) }
            .sortedBy { aggregated -> positions.indexOfFirst { it.symbol.equals(aggregated.symbol, true) } }

    private fun foldLots(symbol: String, lots: List<Position>): AggregatedPosition {
        val totalQuantity = lots.sumOf { it.quantity }
        val totalCost = lots.sumOf { it.quantity * it.averageBuyPrice }
        // A zero net quantity carries no cost information, so the average collapses to 0.
        val weightedAverage = if (totalQuantity == 0.0) 0.0 else totalCost / totalQuantity
        val reference = lots.first()
        return AggregatedPosition(
            symbol = symbol,
            name = reference.name,
            exchange = reference.exchange,
            quantity = totalQuantity,
            averageBuyPrice = weightedAverage,
            currency = reference.currency,
            lotIds = lots.map { it.id },
        )
    }

    /** Values one aggregated position in EUR. */
    fun value(
        position: AggregatedPosition,
        quote: Quote?,
        converter: CurrencyConverter,
    ): PositionValuation {
        val costBasisEur = converter.toEur(position.costBasis, position.currency)
        if (quote == null || costBasisEur == null) {
            return unpriced(position, costBasisEur ?: 0.0)
        }

        val marketValueEur = converter.toEur(position.quantity * quote.price, quote.currency)
        val previousValueEur = converter.toEur(position.quantity * quote.previousClose, quote.currency)
        if (marketValueEur == null || previousValueEur == null) {
            return unpriced(position, costBasisEur)
        }

        return PositionValuation(
            position = position,
            quote = quote,
            marketValueEur = marketValueEur,
            costBasisEur = costBasisEur,
            previousValueEur = previousValueEur,
            dayPnlEur = marketValueEur - previousValueEur,
            totalPnlEur = marketValueEur - costBasisEur,
        )
    }

    private fun unpriced(position: AggregatedPosition, costBasisEur: Double) = PositionValuation(
        position = position,
        quote = null,
        marketValueEur = costBasisEur,
        costBasisEur = costBasisEur,
        previousValueEur = costBasisEur,
        dayPnlEur = 0.0,
        totalPnlEur = 0.0,
    )

    /** Values every aggregated position. [quotes] is looked up case-insensitively by symbol. */
    fun valueAll(
        positions: List<AggregatedPosition>,
        quotes: Map<String, Quote>,
        converter: CurrencyConverter,
    ): List<PositionValuation> {
        val bySymbol = quotes.mapKeys { it.key.uppercase() }
        return positions.map { value(it, bySymbol[it.symbol.uppercase()], converter) }
    }

    /** Rolls per-position valuations into portfolio totals. */
    fun summarize(valuations: List<PositionValuation>): PortfolioSummary {
        if (valuations.isEmpty()) return PortfolioSummary.Empty
        val quoteTimes = valuations.mapNotNull { it.quote?.timestamp }
        return PortfolioSummary(
            totalValueEur = valuations.sumOf { it.marketValueEur },
            costBasisEur = valuations.sumOf { it.costBasisEur },
            previousValueEur = valuations.sumOf { it.previousValueEur },
            dayPnlEur = valuations.sumOf { it.dayPnlEur },
            totalPnlEur = valuations.sumOf { it.totalPnlEur },
            positions = valuations,
            unpricedSymbols = valuations.filterNot { it.isPriced }.map { it.position.symbol },
            lastQuoteAt = quoteTimes.maxOrNull(),
            stalestQuoteAt = quoteTimes.minOrNull(),
        )
    }

    /** Convenience pipeline: raw lots plus quotes plus FX snapshot to a full summary. */
    fun summarize(
        positions: List<Position>,
        quotes: Map<String, Quote>,
        converter: CurrencyConverter,
    ): PortfolioSummary = summarize(valueAll(aggregate(positions), quotes, converter))
}

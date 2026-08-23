package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.market.MarketClock
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Refreshes every symbol the app cares about: held positions plus watchlist.
 *
 * FX follows the quotes because the set of currencies to convert is only known once the
 * quotes are in.
 */
class RefreshMarketDataUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
    private val marketClock: MarketClock,
    private val clock: Clock,
) {

    /**
     * [staleAfter] skips symbols whose cached quote is younger than that age, which is
     * how opening the app avoids re-fetching data it already has. Pass [Duration.ZERO]
     * for an explicit manual refresh, which always hits the network.
     *
     * [respectMarketHours] makes the run bail out without a single request when every
     * symbol follows a market that is shut. The background worker passes true; a refresh
     * the user asked for explicitly passes false, because a deliberate tap should never
     * be silently ignored.
     */
    suspend operator fun invoke(
        staleAfter: Duration = Duration.ZERO,
        respectMarketHours: Boolean = false,
    ): RefreshOutcome {
        val now = Instant.now(clock)
        val positions = portfolioRepository.getPositions()
        val watchlist = watchlistRepository.getItems()
        val allSymbols = (positions.map { it.symbol } + watchlist.map { it.symbol })
            .map { it.uppercase() }
            .distinct()

        if (allSymbols.isEmpty()) return RefreshOutcome.nothingToDo(now)

        // Checked before anything else so a closed-markets run costs no disk reads either.
        if (respectMarketHours && !marketClock.shouldFetch(allSymbols, now)) {
            return RefreshOutcome.marketsClosed(now)
        }

        val cached = quoteRepository.getCachedQuotes(allSymbols)
        val symbols = if (staleAfter.isZero || staleAfter.isNegative) {
            allSymbols
        } else {
            allSymbols.filter { symbol ->
                val quote = cached[symbol]
                quote == null || Duration.between(quote.timestamp, now) >= staleAfter
            }
        }

        val outcome = if (symbols.isEmpty()) {
            RefreshOutcome.nothingToDo(now)
        } else {
            quoteRepository.refreshQuotes(symbols)
        }

        val currencies = buildSet {
            positions.forEach { add(it.currency.uppercase()) }
            quoteRepository.getCachedQuotes(allSymbols).values.forEach { add(it.currency) }
        }.filterNot { it.equals(CurrencyConverter.EUR, ignoreCase = true) }.toSet()

        quoteRepository.refreshFxRates(currencies, FX_MAX_AGE)
        return outcome
    }

    private companion object {
        /** The spec caps FX refreshes at one per hour. */
        val FX_MAX_AGE: Duration = Duration.ofHours(1)
    }
}

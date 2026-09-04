package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.market.MarketClock
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Refreshes the symbols the app actually shows, which is the watchlist.
 *
 * FX follows the quotes because the set of currencies to convert is only known once the
 * quotes are in.
 */
class RefreshMarketDataUseCase @Inject constructor(
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
     * [respectMarketHours] drops the symbols whose own market is shut, and bails out
     * without a single request when that leaves nothing. The background worker passes
     * true; a refresh the user asked for passes false, because a deliberate tap should
     * never be silently ignored.
     */
    suspend operator fun invoke(
        staleAfter: Duration = Duration.ZERO,
        respectMarketHours: Boolean = false,
    ): RefreshOutcome {
        val now = Instant.now(clock)
        val watchlist = watchlistRepository.getItems()
        val allSymbols = watchlist.map { it.symbol.uppercase() }.distinct()

        if (allSymbols.isEmpty()) return RefreshOutcome.nothingToDo(now)

        // Symbol by symbol, not all or nothing: with Wall Street open and Madrid shut,
        // fetching the Spanish half of the list buys a price that cannot have moved.
        // Checked before anything else so a closed run costs no disk reads either.
        val tradeable = if (respectMarketHours) {
            allSymbols.filter { marketClock.shouldFetch(marketClock.marketOf(it), now) }
        } else {
            allSymbols
        }
        if (tradeable.isEmpty()) return RefreshOutcome.marketsClosed(now)

        val cached = quoteRepository.getCachedQuotes(tradeable)
        val symbols = if (staleAfter.isZero || staleAfter.isNegative) {
            tradeable
        } else {
            tradeable.filter { symbol ->
                val quote = cached[symbol]
                quote == null || Duration.between(quote.timestamp, now) >= staleAfter
            }
        }

        val outcome = if (symbols.isEmpty()) {
            RefreshOutcome.nothingToDo(now)
        } else {
            quoteRepository.refreshQuotes(symbols)
        }

        // Only the currencies actually on screen, and only ever once an hour, so an FX
        // call never becomes the reason a run wakes the radio.
        val currencies = quoteRepository.getCachedQuotes(allSymbols).values
            .map { it.currency }
            .filterNot { it.equals(CurrencyConverter.EUR, ignoreCase = true) }
            .toSet()

        quoteRepository.refreshFxRates(currencies, FX_MAX_AGE)
        return outcome
    }

    private companion object {
        /** The spec caps FX refreshes at one per hour. */
        val FX_MAX_AGE: Duration = Duration.ofHours(1)
    }
}

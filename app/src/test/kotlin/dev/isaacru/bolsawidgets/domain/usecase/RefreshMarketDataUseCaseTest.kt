package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.market.MarketClock
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.CandleSeries
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The acceptance criterion behind these tests is "fuera de horario de mercado no se
 * consume red": the assertion is not just the returned outcome but that the repository
 * was never asked for anything.
 */
class RefreshMarketDataUseCaseTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `spends no network at all when every market is shut`() = runTest {
        val quotes = FakeQuoteRepository()
        val useCase = useCaseAt("2026-08-25", 3, 0, quotes)

        val outcome = useCase(respectMarketHours = true)

        assertTrue(outcome.skippedMarketsClosed)
        assertEquals(emptyList<List<String>>(), quotes.quoteRefreshes)
        assertEquals(emptyList<Set<String>>(), quotes.fxRefreshes)
    }

    @Test
    fun `fetches only the symbol whose own market is open`() = runTest {
        val quotes = FakeQuoteRepository()
        // 18:00 in Madrid: the BME closed at 17:35 and its grace ran out at 17:55, but
        // Wall Street is mid-session. Asking Yahoo for SAN.MC here would buy a price that
        // cannot have moved since the close.
        val useCase = useCaseAt("2026-08-25", 18, 0, quotes)

        val outcome = useCase(respectMarketHours = true)

        assertFalse(outcome.skippedMarketsClosed)
        assertEquals(listOf(listOf("AAPL")), quotes.quoteRefreshes)
    }

    @Test
    fun `a closed market is still refreshed when the user asks for it`() = runTest {
        val quotes = FakeQuoteRepository()
        val useCase = useCaseAt("2026-08-25", 18, 0, quotes)

        useCase(respectMarketHours = false)

        assertEquals(listOf(listOf("SAN.MC", "AAPL")), quotes.quoteRefreshes)
    }

    @Test
    fun `a refresh the user asked for ignores market hours`() = runTest {
        val quotes = FakeQuoteRepository()
        val useCase = useCaseAt("2026-08-25", 3, 0, quotes)

        val outcome = useCase(respectMarketHours = false)

        assertFalse(outcome.skippedMarketsClosed)
        assertEquals(listOf(listOf("SAN.MC", "AAPL")), quotes.quoteRefreshes)
    }

    @Test
    fun `skips symbols whose cached quote is still fresh`() = runTest {
        val now = madridAt("2026-08-25", 16, 0)
        val quotes = FakeQuoteRepository(
            cached = mapOf(
                // One minute old, so it does not need refetching.
                "SAN.MC" to quote("SAN.MC", now.minus(Duration.ofMinutes(1))),
                "AAPL" to quote("AAPL", now.minus(Duration.ofHours(2))),
            ),
        )
        val useCase = useCaseAt("2026-08-25", 16, 0, quotes)

        useCase(staleAfter = Duration.ofMinutes(5))

        assertEquals(listOf(listOf("AAPL")), quotes.quoteRefreshes)
    }

    @Test
    fun `asks for the FX of every currency on screen`() = runTest {
        val quotes = FakeQuoteRepository(
            cached = mapOf("AAPL" to quote("AAPL", Instant.EPOCH, currency = "USD")),
        )
        val useCase = useCaseAt("2026-08-25", 16, 0, quotes)

        useCase()

        // EUR is the base currency and never needs a rate of its own.
        assertEquals(listOf(setOf("USD")), quotes.fxRefreshes)
    }

    @Test
    fun `an empty app does nothing`() = runTest {
        val quotes = FakeQuoteRepository()
        val useCase = useCaseAt("2026-08-25", 16, 0, quotes, watchlist = emptyList())

        val outcome = useCase(respectMarketHours = true)

        assertTrue(outcome.requested.isEmpty())
        assertFalse(outcome.skippedMarketsClosed)
        assertEquals(emptyList<List<String>>(), quotes.quoteRefreshes)
    }

    private fun useCaseAt(
        date: String,
        hour: Int,
        minute: Int,
        quotes: FakeQuoteRepository,
        watchlist: List<WatchlistItem> = listOf(
            WatchlistItem("SAN.MC", "Santander", 0),
            WatchlistItem("AAPL", "Apple", 1),
        ),
    ): RefreshMarketDataUseCase {
        val clock = Clock.fixed(madridAt(date, hour, minute), madrid)
        return RefreshMarketDataUseCase(
            watchlistRepository = FakeWatchlistRepository(watchlist),
            quoteRepository = quotes,
            marketClock = MarketClock(clock),
            clock = clock,
        )
    }

    private fun madridAt(date: String, hour: Int, minute: Int): Instant =
        LocalDateTime.parse(
            date + "T" + hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0') + ":00",
        ).atZone(madrid).toInstant()

    private fun quote(symbol: String, at: Instant, currency: String = "EUR") = Quote(
        symbol = symbol,
        price = 10.0,
        previousClose = 10.0,
        currency = currency,
        timestamp = at,
    )

    private class FakeWatchlistRepository(private val items: List<WatchlistItem>) : WatchlistRepository {
        override suspend fun getItems(): List<WatchlistItem> = items
        override fun observeItems(): Flow<List<WatchlistItem>> = flowOf(items)
        override suspend fun add(symbol: String, name: String) = Unit
        override suspend fun remove(symbol: String) = Unit
        override suspend fun setContribution(symbol: String, contribution: Contribution?) = Unit
        override suspend fun setFavorite(symbol: String, favorite: Boolean) = Unit
        override suspend fun reorder(symbolsInOrder: List<String>) = Unit
        override suspend fun replaceAll(items: List<WatchlistItem>) = Unit
    }

    private class FakeQuoteRepository(
        private val cached: Map<String, Quote> = emptyMap(),
    ) : QuoteRepository {
        val quoteRefreshes = mutableListOf<List<String>>()
        val fxRefreshes = mutableListOf<Set<String>>()

        override suspend fun refreshQuotes(symbols: List<String>): RefreshOutcome {
            quoteRefreshes += symbols
            return RefreshOutcome(symbols, symbols, emptyList(), Instant.EPOCH)
        }

        override suspend fun refreshFxRates(currencies: Set<String>, maxAge: Duration): RefreshOutcome {
            if (currencies.isNotEmpty()) fxRefreshes += currencies
            return RefreshOutcome.nothingToDo(Instant.EPOCH)
        }

        override suspend fun getCachedQuotes(symbols: List<String>): Map<String, Quote> =
            cached.filterKeys { it in symbols }

        override fun observeQuotes(): Flow<Map<String, Quote>> = flowOf(cached)
        override fun observeQuote(symbol: String): Flow<Quote?> = flowOf(cached[symbol])
        override fun observeConverter(): Flow<CurrencyConverter> = flowOf(CurrencyConverter.Empty)
        override suspend fun getCandles(
            symbol: String,
            range: ChartRange,
            interval: CandleInterval,
        ): List<Candle> = emptyList()
        override suspend fun getCandleSeries(
            symbol: String,
            range: ChartRange,
            maxAge: Duration,
        ): CandleSeries? = null

        override suspend fun resolveSymbol(symbol: String): Quote? = cached[symbol.uppercase()]
    }
}

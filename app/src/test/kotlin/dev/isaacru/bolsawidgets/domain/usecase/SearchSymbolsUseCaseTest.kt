package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.CandleSeries
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome
import dev.isaacru.bolsawidgets.domain.search.SymbolSearch
import dev.isaacru.bolsawidgets.domain.search.SymbolSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * The picker has to survive the search endpoint disappearing, because it is a second
 * undocumented Yahoo endpoint on top of the one the whole app already depends on.
 */
class SearchSymbolsUseCaseTest {

    @Test
    fun `resolves the typed text as an exact ticker`() = runTest {
        val useCase = SearchSymbolsUseCase(
            quoteRepository = FakeQuoteRepository(resolvable = mapOf("SAN.MC" to quote("SAN.MC"))),
            symbolSearch = FakeSymbolSearch(emptyList()),
        )

        val outcome = useCase("SAN.MC")

        assertEquals("SAN.MC", outcome.exactMatch?.symbol)
        assertFalse(outcome.suggestionsUnavailable)
    }

    @Test
    fun `keeps the exact match when the search endpoint fails`() = runTest {
        val useCase = SearchSymbolsUseCase(
            quoteRepository = FakeQuoteRepository(resolvable = mapOf("SAN.MC" to quote("SAN.MC"))),
            symbolSearch = FakeSymbolSearch(failure = IOException("search down")),
        )

        val outcome = useCase("SAN.MC")

        assertEquals("SAN.MC", outcome.exactMatch?.symbol)
        assertTrue(outcome.suggestions.isEmpty())
        // The UI needs to know so it can tell the user to type the exact ticker.
        assertTrue(outcome.suggestionsUnavailable)
    }

    @Test
    fun `keeps suggestions when the ticker does not exist`() = runTest {
        val useCase = SearchSymbolsUseCase(
            quoteRepository = FakeQuoteRepository(resolvable = emptyMap()),
            symbolSearch = FakeSymbolSearch(listOf(suggestion("SAN.MC"), suggestion("SAN"))),
        )

        val outcome = useCase("santander")

        assertNull(outcome.exactMatch)
        assertEquals(listOf("SAN.MC", "SAN"), outcome.suggestions.map { it.symbol })
    }

    @Test
    fun `does not repeat the exact match inside the suggestions`() = runTest {
        val useCase = SearchSymbolsUseCase(
            quoteRepository = FakeQuoteRepository(resolvable = mapOf("SAN.MC" to quote("SAN.MC"))),
            symbolSearch = FakeSymbolSearch(listOf(suggestion("SAN.MC"), suggestion("SAN"))),
        )

        val outcome = useCase("SAN.MC")

        assertEquals("SAN.MC", outcome.exactMatch?.symbol)
        assertEquals(listOf("SAN"), outcome.suggestions.map { it.symbol })
    }

    @Test
    fun `does not spend a quote request on free text`() = runTest {
        val repository = FakeQuoteRepository(resolvable = emptyMap())
        val useCase = SearchSymbolsUseCase(repository, FakeSymbolSearch(emptyList()))

        useCase("banco santander")

        // A phrase with spaces is not a ticker, so the quote endpoint is left alone.
        assertEquals(emptyList<String>(), repository.resolveCalls)
    }

    @Test
    fun `an empty query does nothing at all`() = runTest {
        val repository = FakeQuoteRepository(resolvable = emptyMap())
        val search = FakeSymbolSearch(emptyList())
        val useCase = SearchSymbolsUseCase(repository, search)

        val outcome = useCase("   ")

        assertTrue(outcome.isEmpty)
        assertEquals(emptyList<String>(), repository.resolveCalls)
        assertEquals(0, search.calls)
    }

    private fun quote(symbol: String) = Quote(
        symbol = symbol,
        price = 12.55,
        previousClose = 12.226,
        currency = "EUR",
        timestamp = Instant.parse("2026-08-23T15:30:00Z"),
        shortName = "BANCO SANTANDER S.A.",
        exchange = "MCE",
    )

    private fun suggestion(symbol: String) =
        SymbolSuggestion(symbol = symbol, name = symbol, exchange = "MCE", type = "Equity")

    private class FakeSymbolSearch(
        private val results: List<SymbolSuggestion> = emptyList(),
        private val failure: Throwable? = null,
    ) : SymbolSearch {
        var calls = 0
            private set

        override suspend fun search(query: String, limit: Int): List<SymbolSuggestion> {
            calls++
            failure?.let { throw it }
            return results
        }
    }

    private class FakeQuoteRepository(
        private val resolvable: Map<String, Quote>,
    ) : QuoteRepository {
        val resolveCalls = mutableListOf<String>()

        override suspend fun resolveSymbol(symbol: String): Quote? {
            resolveCalls += symbol.uppercase()
            return resolvable[symbol.uppercase()]
        }

        override fun observeQuotes(): Flow<Map<String, Quote>> = flowOf(emptyMap())
        override fun observeQuote(symbol: String): Flow<Quote?> = flowOf(null)
        override fun observeConverter(): Flow<CurrencyConverter> = flowOf(CurrencyConverter.Empty)
        override suspend fun getCachedQuotes(symbols: List<String>): Map<String, Quote> = emptyMap()
        override suspend fun refreshQuotes(symbols: List<String>): RefreshOutcome =
            RefreshOutcome.nothingToDo(Instant.EPOCH)

        override suspend fun refreshFxRates(currencies: Set<String>, maxAge: Duration): RefreshOutcome =
            RefreshOutcome.nothingToDo(Instant.EPOCH)

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
    }
}

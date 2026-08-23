package dev.isaacru.bolsawidgets.data.remote.yahoo

import dev.isaacru.bolsawidgets.data.remote.HttpStatusException
import dev.isaacru.bolsawidgets.data.remote.retryWithBackoff
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.search.SymbolSearch
import dev.isaacru.bolsawidgets.domain.search.SymbolSuggestion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooSymbolSearch @Inject constructor(
    private val api: YahooSearchApi,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : SymbolSearch {

    override suspend fun search(query: String, limit: Int): List<SymbolSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        return withContext(io) {
            // A single attempt on purpose: suggestions are optional, and a user typing
            // should not queue up retries behind every keystroke.
            retryWithBackoff(attempts = 1) {
                val response = api.search(query = trimmed, quotesCount = limit)
                if (!response.isSuccessful) {
                    throw HttpStatusException(response.code(), "Yahoo search answered HTTP " + response.code())
                }
                response.body()?.quotes.orEmpty()
                    .asSequence()
                    // Entries Yahoo does not serve itself have no chart data behind them.
                    .filter { it.isYahooFinance != false }
                    .mapNotNull { it.toSuggestion() }
                    .distinctBy { it.symbol }
                    .take(limit)
                    .toList()
            }
        }
    }

    private fun YahooSearchQuote.toSuggestion(): SymbolSuggestion? {
        val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
        return SymbolSuggestion(
            symbol = ticker.uppercase(),
            name = longname ?: shortname ?: ticker,
            exchange = exchDisp ?: exchange.orEmpty(),
            type = typeDisp ?: quoteType.orEmpty(),
        )
    }
}

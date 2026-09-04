package dev.isaacru.bolsawidgets.domain.model

import java.time.Instant

/** Which end of the day's ranking a list holds. */
enum class MoverDirection {
    GAINERS,
    LOSERS,
}

/**
 * One company in today's ranking, as the provider's screener hands it over.
 *
 * Everything needed to draw the row travels in the same response, which is the whole
 * reason this is affordable: the ranking is one request, not one request per company.
 */
data class MarketMover(
    val symbol: String,
    val name: String,
    val exchange: String,
    val price: Double,
    val currency: String,
    val changePercent: Double,
)

/**
 * The two ends of the day, with the moment they were fetched.
 *
 * [fetchedAt] is shown rather than hidden: this is a list that ages, and one from before
 * lunch that says so is more useful than one that pretends to be live.
 */
data class MarketMovers(
    val gainers: List<MarketMover> = emptyList(),
    val losers: List<MarketMover> = emptyList(),
    val fetchedAt: Instant? = null,
) {
    val isEmpty: Boolean get() = gainers.isEmpty() && losers.isEmpty()
}

package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.model.MarketMovers
import java.time.Duration

/**
 * Today's biggest movers, cache-first.
 *
 * The list is only ever fetched because the user asked to look at it, never from the
 * background worker: it is a screen, not something a widget depends on.
 */
interface MoversRepository {

    /**
     * Returns the cached ranking while it is younger than [maxAge], otherwise fetches a
     * new one. A failed fetch falls back to whatever is cached, so the screen shows the
     * morning's ranking with its timestamp rather than an error.
     */
    suspend fun getMovers(maxAge: Duration): MarketMovers
}

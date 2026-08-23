package dev.isaacru.bolsawidgets.domain.market

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether it is worth spending network on a set of symbols.
 *
 * This is what keeps the background worker from waking the radio all night. It takes an
 * injected [Clock] so the whole thing is testable without waiting for 17:35.
 */
@Singleton
class MarketClock @Inject constructor(private val clock: Clock) {

    fun now(): Instant = Instant.now(clock)

    fun marketOf(symbol: String): Market = Market.of(symbol)

    /** True while [market] is inside its regular session on a weekday. */
    fun isOpen(market: Market, at: Instant = now()): Boolean {
        if (market.isAlwaysOpen) return true
        val local = at.atZone(market.zone)
        if (local.isWeekend()) return false
        return !local.isBefore(local.at(market.open)) && !local.isAfter(local.at(market.close))
    }

    fun isOpen(symbol: String, at: Instant = now()): Boolean = isOpen(marketOf(symbol), at)

    fun anyOpen(symbols: Collection<String>, at: Instant = now()): Boolean =
        symbols.any { isOpen(it, at) }

    /**
     * Whether [market] deserves a fetch right now.
     *
     * The session window is extended by [CLOSING_GRACE] so one run lands after the close
     * and captures the official closing price. Without it the last stored quote would be
     * whatever the final in-session run happened to see, and the day change would stay
     * subtly wrong until the next morning.
     */
    fun shouldFetch(market: Market, at: Instant = now()): Boolean {
        if (market.isAlwaysOpen) return true
        val local = at.atZone(market.zone)
        if (local.isWeekend()) return false
        val closeWithGrace = local.at(market.close).plus(CLOSING_GRACE)
        return !local.isBefore(local.at(market.open)) && !local.isAfter(closeWithGrace)
    }

    /**
     * Whether any of [symbols] is worth fetching. An empty set is never worth a request.
     * This is the single call the background worker makes before touching the network.
     */
    fun shouldFetch(symbols: Collection<String>, at: Instant = now()): Boolean =
        symbols.isNotEmpty() && symbols.any { shouldFetch(marketOf(it), at) }

    /** The venues behind [symbols], for logging and for explaining a skipped run. */
    fun marketsOf(symbols: Collection<String>): Set<Market> =
        symbols.mapTo(mutableSetOf()) { marketOf(it) }

    private fun ZonedDateTime.isWeekend(): Boolean =
        dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    private fun ZonedDateTime.at(time: java.time.LocalTime): ZonedDateTime = with(time)

    companion object {
        /** How long after the close a refresh is still worth making. */
        val CLOSING_GRACE: Duration = Duration.ofMinutes(20)
    }
}

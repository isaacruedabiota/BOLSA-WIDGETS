package dev.isaacru.bolsawidgets.domain.market

import java.time.LocalTime
import java.time.ZoneId

/**
 * A trading venue and its regular session.
 *
 * Hours are declared in the venue's **own** timezone rather than in Madrid time. Europe
 * and the United States do not switch to summer time on the same dates, so for a couple
 * of weeks a year the New York session runs 14:30-21:00 Madrid time instead of the usual
 * 15:30-22:00. Declaring 09:30-16:00 New York keeps that correct for free.
 *
 * Holidays are ignored in v1, as agreed: the cost of an unnecessary fetch on 25 December
 * is a handful of requests.
 */
enum class Market(
    zoneName: String,
    val open: LocalTime,
    val close: LocalTime,
    val suffixes: Set<String>,
) {
    /** Bolsa de Madrid: continuous session plus the closing auction. */
    BME("Europe/Madrid", LocalTime.of(9, 0), LocalTime.of(17, 35), setOf("MC")),

    /** NYSE and NASDAQ, i.e. 15:30-22:00 Madrid time outside the DST mismatch weeks. */
    US("America/New_York", LocalTime.of(9, 30), LocalTime.of(16, 0), emptySet()),

    EURONEXT("Europe/Paris", LocalTime.of(9, 0), LocalTime.of(17, 40), setOf("AS", "PA", "BR", "LS")),

    XETRA("Europe/Berlin", LocalTime.of(9, 0), LocalTime.of(17, 30), setOf("DE", "F", "SG", "MU")),

    BORSA_ITALIANA("Europe/Rome", LocalTime.of(9, 0), LocalTime.of(17, 30), setOf("MI")),

    SIX("Europe/Zurich", LocalTime.of(9, 0), LocalTime.of(17, 30), setOf("SW")),

    LONDON("Europe/London", LocalTime.of(8, 0), LocalTime.of(16, 30), setOf("L")),

    /**
     * Anything the table does not recognise: indices, FX pairs, crypto, exotic venues.
     * Treated as permanently open so an unknown instrument is never starved of updates.
     * Fetching too often is a battery cost; not fetching is wrong data.
     */
    UNKNOWN("UTC", LocalTime.MIN, LocalTime.MAX, emptySet());

    val zone: ZoneId = ZoneId.of(zoneName)

    /** True when this venue has no session boundaries to respect. */
    val isAlwaysOpen: Boolean get() = this == UNKNOWN

    companion object {

        /**
         * Guesses the venue from a Yahoo ticker.
         *
         * Yahoo suffixes the exchange after a dot ("SAN.MC", "IWDA.AS") and leaves US
         * listings bare ("AAPL"). Indices are prefixed with "^" and FX pairs end in "=X";
         * neither has an equity session, so both fall through to [UNKNOWN].
         *
         * A hyphen also falls through, which sweeps up crypto pairs ("BTC-EUR") along
         * with US class shares ("BRK-B"). That is the deliberate direction of the error:
         * fetching BRK-B outside NYSE hours wastes a few requests, whereas treating a
         * 24/7 instrument as an equity would leave a stale price on screen all night.
         */
        fun of(symbol: String): Market {
            val ticker = symbol.trim().uppercase()
            if (ticker.isEmpty()) return UNKNOWN
            if (ticker.startsWith("^") || ticker.endsWith("=X") || ticker.contains("-")) return UNKNOWN

            val suffix = ticker.substringAfterLast('.', missingDelimiterValue = "")
            if (suffix.isEmpty()) return US
            return entries.firstOrNull { suffix in it.suffixes } ?: UNKNOWN
        }
    }
}

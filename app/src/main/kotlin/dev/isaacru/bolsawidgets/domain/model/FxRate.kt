package dev.isaacru.bolsawidgets.domain.model

import java.time.Instant

/**
 * Exchange rate for [pair], expressed as "1 base unit = [rate] quote units".
 * [pair] is the concatenation of both ISO codes, e.g. "USDEUR".
 */
data class FxRate(
    val pair: String,
    val rate: Double,
    val fetchedAt: Instant,
) {
    companion object {
        fun pairOf(from: String, to: String): String = from.uppercase() + to.uppercase()
    }
}

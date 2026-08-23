package dev.isaacru.bolsawidgets.domain.model

import java.time.Instant

/**
 * Point-in-time price snapshot for a single symbol.
 *
 * [previousClose] is the close of the previous trading session; every "daily change"
 * shown in the app and the widgets is measured against it.
 */
data class Quote(
    val symbol: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val timestamp: Instant,
    val shortName: String? = null,
    val exchange: String? = null,
) {
    val change: Double
        get() = price - previousClose

    val changePercent: Double
        get() = if (previousClose <= 0.0) 0.0 else (change / previousClose) * 100.0
}

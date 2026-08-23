package dev.isaacru.bolsawidgets.domain.model

import java.time.Instant

/** A single OHLCV bar. Used to draw sparklines and the detail chart. */
data class Candle(
    val timestamp: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

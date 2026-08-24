package dev.isaacru.bolsawidgets.data.local

import dev.isaacru.bolsawidgets.domain.model.Candle
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Storage shape of one candle.
 *
 * Field names are single letters because this is written thousands of times per series
 * and the whole thing lives in one TEXT column; the long names buy nothing here.
 */
@Serializable
data class CandleWire(
    val t: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Long,
)

fun Candle.toWire() = CandleWire(
    t = timestamp.epochSecond,
    o = open,
    h = high,
    l = low,
    c = close,
    v = volume,
)

fun CandleWire.toDomain() = Candle(
    timestamp = Instant.ofEpochSecond(t),
    open = o,
    high = h,
    low = l,
    close = c,
    volume = v,
)

package dev.isaacru.bolsawidgets.data.remote

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Runs [block], retrying transient failures with exponential backoff.
 *
 * Market-data endpoints are treated as fragile, but retrying a "symbol does not exist"
 * response only wastes battery, so [shouldRetry] decides. The last failure is rethrown.
 */
suspend fun <T> retryWithBackoff(
    attempts: Int = DEFAULT_ATTEMPTS,
    initialDelayMillis: Long = 300L,
    maxDelayMillis: Long = 2_000L,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = ::isTransient,
    block: suspend () -> T,
): T {
    require(attempts >= 1) { "attempts must be at least 1" }
    var delayMillis = initialDelayMillis
    repeat(attempts - 1) {
        try {
            return block()
        } catch (error: Throwable) {
            if (!shouldRetry(error)) throw error
            delay(delayMillis)
            delayMillis = (delayMillis * factor).toLong().coerceAtMost(maxDelayMillis)
        }
    }
    return block()
}

/** Network hiccups and server-side throttling are worth a second try; anything else is not. */
fun isTransient(error: Throwable): Boolean = when (error) {
    is IOException -> true
    is HttpStatusException -> error.code == 429 || error.code >= 500
    else -> false
}

/** A non-2xx response from a market-data backend. */
class HttpStatusException(
    val code: Int,
    message: String,
) : Exception(message)

const val DEFAULT_ATTEMPTS = 3

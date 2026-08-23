package dev.isaacru.bolsawidgets.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    @Test
    fun `returns the first successful result without retrying`() = runTest {
        var calls = 0

        val result = retryWithBackoff { calls++; "ok" }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries a transient failure and then succeeds`() = runTest {
        var calls = 0

        val result = retryWithBackoff {
            calls++
            if (calls < 3) throw IOException("connection reset") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `gives up after the configured number of attempts`() = runTest {
        var calls = 0

        assertThrows(IOException::class.java) {
            runBlockingRetry { calls++; throw IOException("still down") }
        }

        assertEquals(DEFAULT_ATTEMPTS, calls)
    }

    @Test
    fun `does not retry a response the server will keep rejecting`() = runTest {
        var calls = 0

        assertThrows(HttpStatusException::class.java) {
            runBlockingRetry { calls++; throw HttpStatusException(404, "not found") }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `retries throttling and server errors`() {
        assertEquals(true, isTransient(HttpStatusException(429, "too many requests")))
        assertEquals(true, isTransient(HttpStatusException(503, "unavailable")))
        assertEquals(false, isTransient(HttpStatusException(400, "bad request")))
        assertEquals(true, isTransient(IOException("timeout")))
        assertEquals(false, isTransient(IllegalStateException("bug")))
    }

    /** assertThrows needs a non-suspending lambda, so the coroutine is bridged here. */
    private fun <T> runBlockingRetry(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { retryWithBackoff(initialDelayMillis = 1L, block = block) }
}

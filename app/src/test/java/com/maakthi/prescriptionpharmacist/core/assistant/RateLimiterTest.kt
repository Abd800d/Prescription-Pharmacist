package com.maakthi.prescriptionpharmacist.core.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client-side sliding-window rate limiter. */
class RateLimiterTest {

    @Test
    fun `allows requests up to the limit then blocks`() {
        val limiter = RateLimiter(maxPerWindow = 3, windowMillis = 60_000)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(1_000))
        assertTrue(limiter.tryAcquire(2_000))
        assertFalse(limiter.tryAcquire(3_000))
    }

    @Test
    fun `frees slots after the window slides`() {
        val limiter = RateLimiter(maxPerWindow = 2, windowMillis = 60_000)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(1_000))
        assertFalse(limiter.tryAcquire(2_000))
        assertTrue(limiter.tryAcquire(61_000))
    }

    @Test
    fun `retryAfter reports the wait time`() {
        val limiter = RateLimiter(maxPerWindow = 1, windowMillis = 60_000)
        assertTrue(limiter.tryAcquire(10_000))
        val wait = limiter.retryAfterMillis(15_000)
        assertTrue("got $wait", wait in 55_000..55_001)
        assertEqualsish(0L, limiter.retryAfterMillis(71_000))
    }

    private fun assertEqualsish(expected: Long, actual: Long) {
        assertTrue("expected $expected got $actual", actual == expected)
    }
}

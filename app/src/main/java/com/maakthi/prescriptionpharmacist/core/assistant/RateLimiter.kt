package com.maakthi.prescriptionpharmacist.core.assistant

/**
 * Sliding-window rate limiter (client-side companion of the server limiter).
 * Pure and unit-testable; the server enforces the authoritative limits.
 */
class RateLimiter(
    private val maxPerWindow: Int,
    private val windowMillis: Long
) {
    private val stamps = ArrayDeque<Long>()

    /** Returns true (and records the call) when under the limit. */
    @Synchronized
    fun tryAcquire(now: Long): Boolean {
        while (stamps.isNotEmpty() && now - stamps.first() >= windowMillis) {
            stamps.removeFirst()
        }
        if (stamps.size >= maxPerWindow) return false
        stamps.addLast(now)
        return true
    }

    /** Milliseconds until the oldest slot frees up; 0 when a slot is free. */
    @Synchronized
    fun retryAfterMillis(now: Long): Long {
        while (stamps.isNotEmpty() && now - stamps.first() >= windowMillis) {
            stamps.removeFirst()
        }
        return if (stamps.size < maxPerWindow) 0L
        else (stamps.first() + windowMillis - now).coerceAtLeast(0L)
    }
}

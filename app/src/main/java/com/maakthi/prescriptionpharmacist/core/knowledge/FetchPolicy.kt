package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Network resilience policy: per-call timeout + bounded retries.
 *
 * A call that times out, throws, or returns an error is retried up to
 * [attempts] times; the last failure is returned as an [AppResult.Error]
 * so callers can degrade gracefully instead of crashing.
 */
object FetchPolicy {

    const val DEFAULT_ATTEMPTS = 2
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val RETRY_BACKOFF_MS = 400L

    suspend fun <T> withRetry(
        attempts: Int = DEFAULT_ATTEMPTS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        backoffMs: Long = RETRY_BACKOFF_MS,
        block: suspend () -> AppResult<T>
    ): AppResult<T> {
        var last: AppResult<T> = AppResult.error(ErrorCode.NETWORK, "unreachable")
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val outcome: AppResult<T>? = withTimeoutOrNull(timeoutMs) {
                try {
                    block()
                } catch (e: Exception) {
                    AppResult.error(ErrorCode.NETWORK, e.message, e)
                }
            }
            val result = outcome ?: AppResult.error(ErrorCode.NETWORK, "timeout")
            if (result.isSuccess) return result
            last = result
            if (attempt < attempts - 1) delay(backoffMs)
        }
        return last
    }
}

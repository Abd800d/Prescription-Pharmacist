package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Network resilience: retries, recovery from transient failures, timeouts —
 * the offline-first experience depends on this policy degrading gracefully.
 */
class FetchPolicyTest {

    @Test
    fun `success on the first call is returned immediately`() = runTest {
        var calls = 0
        val result = FetchPolicy.withRetry(attempts = 3, backoffMs = 0) {
            calls++
            AppResult.success("ok")
        }
        assertTrue(result.isSuccess)
        assertEquals(1, calls)
    }

    @Test
    fun `transient failure is retried and then succeeds`() = runTest {
        var calls = 0
        val result = FetchPolicy.withRetry(attempts = 3, backoffMs = 0) {
            calls++
            if (calls == 1) AppResult.error(ErrorCode.NETWORK, "blip") else AppResult.success("ok")
        }
        assertTrue(result.isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun `persistent failure returns the last error after all attempts`() = runTest {
        var calls = 0
        val result = FetchPolicy.withRetry<String>(attempts = 3, backoffMs = 0) {
            calls++
            AppResult.error(ErrorCode.NETWORK, "still down")
        }
        assertTrue(result is AppResult.Error)
        assertEquals(3, calls)
    }

    @Test
    fun `thrown exceptions are converted to network errors`() = runTest {
        val result = FetchPolicy.withRetry<String>(attempts = 2, backoffMs = 0) {
            throw java.io.IOException("connection reset")
        }
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.NETWORK, (result as AppResult.Error).code)
    }

    @Test
    fun `a hanging call times out and is retried`() = runTest {
        var calls = 0
        val result = FetchPolicy.withRetry<String>(attempts = 2, timeoutMs = 50, backoffMs = 0) {
            calls++
            kotlinx.coroutines.delay(10_000) // hangs past the timeout
            AppResult.success("too late")
        }
        assertTrue(result is AppResult.Error)
        assertEquals(2, calls)
    }
}

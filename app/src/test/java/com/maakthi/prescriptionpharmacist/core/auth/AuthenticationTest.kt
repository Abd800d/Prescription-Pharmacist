package com.maakthi.prescriptionpharmacist.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Authentication error mapping: every Firebase failure mode becomes a
 * friendly, actionable type — raw provider messages never reach the user.
 */
class AuthenticationTest {

    @Test
    fun `registration conflicts map to email exists`() {
        assertEquals(AuthErrorType.EMAIL_EXISTS, AuthErrorMapper.map("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthErrorType.EMAIL_EXISTS, AuthErrorMapper.map("email-already-in-use"))
    }

    @Test
    fun `bad credentials map to wrong credentials`() {
        assertEquals(AuthErrorType.WRONG_CREDENTIALS, AuthErrorMapper.map("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthErrorType.WRONG_CREDENTIALS, AuthErrorMapper.map("INVALID_LOGIN_CREDENTIALS"))
        assertEquals(AuthErrorType.WRONG_CREDENTIALS, AuthErrorMapper.map("invalid-credential"))
    }

    @Test
    fun `weak password and invalid email are distinguished`() {
        assertEquals(AuthErrorType.WEAK_PASSWORD, AuthErrorMapper.map("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthErrorType.INVALID_EMAIL, AuthErrorMapper.map("ERROR_INVALID_EMAIL"))
    }

    @Test
    fun `account states map correctly`() {
        assertEquals(AuthErrorType.USER_NOT_FOUND, AuthErrorMapper.map("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthErrorType.USER_DISABLED, AuthErrorMapper.map("ERROR_USER_DISABLED"))
        assertEquals(AuthErrorType.REQUIRES_RECENT_LOGIN, AuthErrorMapper.map("ERROR_REQUIRES_RECENT_LOGIN"))
        assertEquals(AuthErrorType.EMAIL_NOT_VERIFIED, AuthErrorMapper.map("not-verified"))
    }

    @Test
    fun `rate limits and network problems map to retryable types`() {
        assertEquals(AuthErrorType.TOO_MANY_REQUESTS, AuthErrorMapper.map("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(AuthErrorType.NETWORK_ERROR, AuthErrorMapper.map("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(AuthErrorType.NETWORK_ERROR, AuthErrorMapper.map("timeout"))
        assertEquals(AuthErrorType.NETWORK_ERROR, AuthErrorMapper.map("UNAVAILABLE"))
    }

    @Test
    fun `missing configuration and unknown codes are safe`() {
        assertEquals(AuthErrorType.NOT_CONFIGURED, AuthErrorMapper.map("NOT_CONFIGURED"))
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map("some-random-firebase-code"))
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map(null))
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map(""))
    }
}

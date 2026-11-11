package com.maakthi.prescriptionpharmacist.core.auth

import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import com.maakthi.prescriptionpharmacist.domain.model.userRoleOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Authentication error mapping (Firebase codes → friendly types) and
 * role-claim parsing. Pure JVM — no Firebase required.
 */
class AuthErrorMapperTest {

    @Test
    fun `firebase error codes map to friendly types`() {
        assertEquals(AuthErrorType.EMAIL_EXISTS, AuthErrorMapper.map("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthErrorType.INVALID_EMAIL, AuthErrorMapper.map("ERROR_INVALID_EMAIL"))
        assertEquals(AuthErrorType.WEAK_PASSWORD, AuthErrorMapper.map("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthErrorType.WRONG_CREDENTIALS, AuthErrorMapper.map("ERROR_WRONG_PASSWORD"))
        assertEquals(
            AuthErrorType.WRONG_CREDENTIALS,
            AuthErrorMapper.map("ERROR_INVALID_LOGIN_CREDENTIALS")
        )
        assertEquals(AuthErrorType.USER_NOT_FOUND, AuthErrorMapper.map("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthErrorType.USER_DISABLED, AuthErrorMapper.map("ERROR_USER_DISABLED"))
        assertEquals(AuthErrorType.TOO_MANY_REQUESTS, AuthErrorMapper.map("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(
            AuthErrorType.NETWORK_ERROR,
            AuthErrorMapper.map("ERROR_NETWORK_REQUEST_FAILED")
        )
        assertEquals(
            AuthErrorType.REQUIRES_RECENT_LOGIN,
            AuthErrorMapper.map("ERROR_REQUIRES_RECENT_LOGIN")
        )
        assertEquals(AuthErrorType.NOT_CONFIGURED, AuthErrorMapper.map("NOT_CONFIGURED"))
    }

    @Test
    fun `unknown codes fall back safely`() {
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map("ERROR_SOMETHING_NEW"))
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map(null))
        assertEquals(AuthErrorType.UNKNOWN, AuthErrorMapper.map(""))
    }

    @Test
    fun `role claims parse with safe defaults`() {
        assertEquals(UserRole.ADMIN, userRoleOf("admin"))
        assertEquals(UserRole.PHARMACIST, userRoleOf("pharmacist"))
        assertEquals(UserRole.USER, userRoleOf("user"))
        assertEquals(UserRole.USER, userRoleOf(null))
        assertEquals(UserRole.USER, userRoleOf("root"))
        assertEquals(UserRole.PHARMACIST, userRoleOf("PHARMACIST"))
    }
}

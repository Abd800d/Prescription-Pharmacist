package com.maakthi.prescriptionpharmacist.core.auth

/**
 * Friendly, actionable authentication error types.
 *
 * Firebase error codes are mapped here so the UI never shows raw Firebase
 * messages, and so the mapping is unit-testable without Firebase.
 */
enum class AuthErrorType {
    EMAIL_EXISTS,
    INVALID_EMAIL,
    WEAK_PASSWORD,
    WRONG_CREDENTIALS,
    USER_NOT_FOUND,
    USER_DISABLED,
    TOO_MANY_REQUESTS,
    NETWORK_ERROR,
    REQUIRES_RECENT_LOGIN,
    EMAIL_NOT_VERIFIED,
    INVALID_CREDENTIAL,
    NOT_CONFIGURED,
    UNKNOWN
}

object AuthErrorMapper {

    /** Maps a Firebase `FirebaseAuthException.errorCode` (or a heuristic message) to [AuthErrorType]. */
    fun map(codeOrMessage: String?): AuthErrorType {
        val raw = codeOrMessage.orEmpty()
        val code = raw.uppercase()
        return when {
            "ALREADY_IN_USE" in code || "EMAIL_EXISTS" in code -> AuthErrorType.EMAIL_EXISTS
            "INVALID_EMAIL" in code -> AuthErrorType.INVALID_EMAIL
            "WEAK_PASSWORD" in code -> AuthErrorType.WEAK_PASSWORD
            "WRONG_PASSWORD" in code ||
                "INVALID_LOGIN_CREDENTIALS" in code ||
                "INVALID_CREDENTIAL" in code -> AuthErrorType.WRONG_CREDENTIALS
            "USER_NOT_FOUND" in code || "NO_USER" in code -> AuthErrorType.USER_NOT_FOUND
            "USER_DISABLED" in code -> AuthErrorType.USER_DISABLED
            "TOO_MANY_REQUEST" in code || "TOO_MANY_ATTEMPTS" in code -> AuthErrorType.TOO_MANY_REQUESTS
            "NETWORK" in code || "TIMEOUT" in code || "UNAVAILABLE" in code -> AuthErrorType.NETWORK_ERROR
            "REQUIRES_RECENT_LOGIN" in code -> AuthErrorType.REQUIRES_RECENT_LOGIN
            "NOT_VERIFIED" in code || "EMAIL_NOT_VERIFIED" in code -> AuthErrorType.EMAIL_NOT_VERIFIED
            "NOT_CONFIGURED" in code -> AuthErrorType.NOT_CONFIGURED
            else -> AuthErrorType.UNKNOWN
        }
    }
}

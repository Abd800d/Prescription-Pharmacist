package com.maakthi.prescriptionpharmacist.domain.model

/**
 * A lightweight result type used across layers instead of exceptions.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val code: ErrorCode,
        val message: String? = null,
        val cause: Throwable? = null
    ) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun error(code: ErrorCode, message: String? = null, cause: Throwable? = null): AppResult<Nothing> =
            Error(code, message, cause)
    }
}

enum class ErrorCode {
    NOT_CONFIGURED,
    NETWORK,
    UNAUTHORIZED,
    SERVER,
    ANALYSIS_FAILED,
    IMAGE_UNREADABLE,
    VALIDATION,
    DUPLICATE,
    NOT_FOUND,
    RATE_LIMITED,
    UNKNOWN
}

package com.maakthi.prescriptionpharmacist.data.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared backend access for the provider engines (OCR + AI extraction):
 * resolves the configurable base URL and the Firebase ID token. API keys
 * never live in the app — the backend holds them in environment secrets.
 */
@Singleton
class BackendConfig @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) {

    /** Empty when no backend is configured (the app shows a clear message). */
    suspend fun baseUrl(): String {
        val fromSettings = settingsRepository.settings.first().backendUrl
        return when {
            fromSettings.isNotBlank() -> fromSettings.trimEnd('/')
            else -> com.maakthi.prescriptionpharmacist.BuildConfig.ANALYSIS_BASE_URL.trimEnd('/')
        }
    }

    suspend fun authorization(): String? =
        authRepository.getIdToken()?.let { "Bearer $it" }

    fun errorForHttp(code: Int): AppResult<Nothing> {
        val errorCode = when (code) {
            401, 403 -> ErrorCode.UNAUTHORIZED
            in 500..599 -> ErrorCode.SERVER
            else -> ErrorCode.ANALYSIS_FAILED
        }
        return AppResult.error(errorCode, "HTTP $code")
    }
}

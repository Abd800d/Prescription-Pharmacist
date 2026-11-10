package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.AnalysisResponseDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.MedicationDto
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.MedicationCandidate
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf
import com.maakthi.prescriptionpharmacist.domain.repository.AnalysisRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAnalysisRepository @Inject constructor(
    private val api: AnalysisApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : AnalysisRepository {

    override suspend fun analyzeImage(imageBytes: ByteArray): AppResult<AnalysisResult> {
        val baseUrl = resolveBaseUrl()
        if (baseUrl.isEmpty()) {
            return AppResult.error(ErrorCode.NOT_CONFIGURED)
        }

        val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", "prescription.jpg", body)
        val authorization = authRepository.getIdToken()?.let { "Bearer $it" }

        return try {
            val response = api.analyze(
                url = "${baseUrl.trimEnd('/')}/analyze",
                image = part,
                authorization = authorization
            )
            mapResponse(response)
        } catch (e: IOException) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        } catch (e: SerializationException) {
            AppResult.error(ErrorCode.SERVER, cause = e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }

    private suspend fun resolveBaseUrl(): String {
        val fromSettings = settingsRepository.settings.first().backendUrl
        return when {
            fromSettings.isNotBlank() -> fromSettings
            else -> com.maakthi.prescriptionpharmacist.BuildConfig.ANALYSIS_BASE_URL
        }
    }

    private fun mapResponse(
        response: retrofit2.Response<AnalysisResponseDto>
    ): AppResult<AnalysisResult> {
        if (!response.isSuccessful) {
            val code = when (response.code()) {
                401, 403 -> ErrorCode.UNAUTHORIZED
                in 500..599 -> ErrorCode.SERVER
                else -> ErrorCode.ANALYSIS_FAILED
            }
            return AppResult.error(code, "HTTP ${response.code()}")
        }
        val dto = response.body()
            ?: return AppResult.error(ErrorCode.ANALYSIS_FAILED, "Empty response body")

        if (dto.error != null) {
            return AppResult.error(
                ErrorCode.ANALYSIS_FAILED,
                dto.error.message ?: dto.error.code
            )
        }

        val medications = dto.medications.mapNotNull { it.toDomainOrNull() }
        return AppResult.success(
            AnalysisResult(
                medications = medications,
                language = dto.language,
                notes = dto.notes,
                requestId = dto.requestId
            )
        )
    }

    private fun MedicationDto.toDomainOrNull(): MedicationCandidate? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null
        return MedicationCandidate(
            name = trimmedName,
            scientificName = scientificName?.trim()?.takeIf { it.isNotEmpty() },
            type = medicationTypeOf(type),
            strength = strength?.trim()?.takeIf { it.isNotEmpty() },
            form = form?.trim()?.takeIf { it.isNotEmpty() },
            confidence = confidence.toFloat().coerceIn(0f, 1f),
            rawText = rawText?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

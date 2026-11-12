package com.maakthi.prescriptionpharmacist.data.analysis

import com.maakthi.prescriptionpharmacist.core.analysis.AiExtractionEngine
import com.maakthi.prescriptionpharmacist.core.analysis.AiMedicationHint
import com.maakthi.prescriptionpharmacist.data.remote.AnalysisApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.ExtractRequestDto
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [AiExtractionEngine] implementation: posts OCR text to the secure
 * backend `/extract` endpoint (vision-LLM guided by a strict no-invention
 * prompt). Swappable with any other provider without touching the pipeline;
 * [com.maakthi.prescriptionpharmacist.core.analysis.LocalRuleBasedExtractionEngine]
 * is the offline fallback.
 */
@Singleton
class RemoteAiExtractionEngine @Inject constructor(
    private val api: AnalysisApi,
    private val backendConfig: BackendConfig
) : AiExtractionEngine {

    override suspend fun extract(ocrText: String): AppResult<List<AiMedicationHint>> {
        val baseUrl = backendConfig.baseUrl()
        if (baseUrl.isEmpty()) {
            return AppResult.error(ErrorCode.NOT_CONFIGURED)
        }
        if (ocrText.isBlank()) {
            return AppResult.success(emptyList())
        }
        return try {
            val response = api.extract(
                url = "$baseUrl/extract",
                body = ExtractRequestDto(text = ocrText),
                authorization = backendConfig.authorization()
            )
            if (!response.isSuccessful) {
                return backendConfig.errorForHttp(response.code())
            }
            val dto = response.body()
                ?: return AppResult.error(ErrorCode.ANALYSIS_FAILED, "Empty extract response")
            if (dto.error != null) {
                return AppResult.error(
                    ErrorCode.ANALYSIS_FAILED,
                    dto.error.message ?: dto.error.code
                )
            }
            AppResult.success(
                dto.hints.map {
                    AiMedicationHint(
                        name = it.name,
                        scientificName = it.scientificName,
                        type = it.type,
                        strength = it.strength,
                        form = it.form,
                        dose = it.dose,
                        route = it.route,
                        duration = it.duration,
                        rawText = it.rawText,
                        unclear = it.unclear,
                        alternatives = it.alternatives
                    )
                }
            )
        } catch (e: IOException) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        } catch (e: SerializationException) {
            AppResult.error(ErrorCode.SERVER, cause = e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }
}

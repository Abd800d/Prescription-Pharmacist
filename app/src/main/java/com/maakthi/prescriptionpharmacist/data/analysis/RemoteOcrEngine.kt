package com.maakthi.prescriptionpharmacist.data.analysis

import com.maakthi.prescriptionpharmacist.core.analysis.OcrEngine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrLine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrResult
import com.maakthi.prescriptionpharmacist.data.remote.AnalysisApi
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [OcrEngine] implementation: sends the (already enhanced/compressed)
 * prescription image to the secure backend `/ocr` endpoint and receives the
 * transcribed Arabic/English text. Swappable with an on-device engine later
 * without touching the pipeline.
 */
@Singleton
class RemoteOcrEngine @Inject constructor(
    private val api: AnalysisApi,
    private val backendConfig: BackendConfig
) : OcrEngine {

    override suspend fun recognize(imageBytes: ByteArray): AppResult<OcrResult> {
        val baseUrl = backendConfig.baseUrl()
        if (baseUrl.isEmpty()) {
            return AppResult.error(ErrorCode.NOT_CONFIGURED)
        }
        val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", "prescription.jpg", body)

        return try {
            val response = api.ocr(
                url = "$baseUrl/ocr",
                image = part,
                authorization = backendConfig.authorization()
            )
            if (!response.isSuccessful) {
                return backendConfig.errorForHttp(response.code())
            }
            val dto = response.body()
                ?: return AppResult.error(ErrorCode.ANALYSIS_FAILED, "Empty OCR response")
            if (dto.error != null) {
                return AppResult.error(
                    ErrorCode.ANALYSIS_FAILED,
                    dto.error.message ?: dto.error.code
                )
            }
            if (dto.text.isBlank() && dto.lines.isEmpty()) {
                return AppResult.error(ErrorCode.ANALYSIS_FAILED, "OCR returned no text")
            }
            AppResult.success(
                OcrResult(
                    fullText = dto.text,
                    lines = dto.lines.map {
                        OcrLine(text = it.text, confidence = it.confidence.toFloat().coerceIn(0f, 1f))
                    },
                    language = dto.language,
                    requestId = dto.requestId
                )
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

package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.AnalysisResponseDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.ExtractRequestDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.ExtractResponseDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.OcrResponseDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * The analysis backend is a configurable, secure HTTPS endpoint. The absolute
 * request URL is provided per-call so the pharmacist can change the backend
 * from Settings without rebuilding the app.
 *
 * Three independent operations:
 * - /ocr     — stage 3: image → raw text (OCR layer).
 * - /extract — stages 5–8: OCR text → structured hints (AI layer).
 * - /analyze — legacy one-shot image → medications.
 */
interface AnalysisApi {

    @Multipart
    @POST("ocr")
    suspend fun ocr(
        @Url url: String,
        @Part image: MultipartBody.Part,
        @Header("Authorization") authorization: String? = null
    ): Response<OcrResponseDto>

    @POST("extract")
    suspend fun extract(
        @Url url: String,
        @Body body: ExtractRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<ExtractResponseDto>

    @Multipart
    @POST("analyze")
    suspend fun analyze(
        @Url url: String,
        @Part image: MultipartBody.Part,
        @Header("Authorization") authorization: String? = null
    ): Response<AnalysisResponseDto>
}

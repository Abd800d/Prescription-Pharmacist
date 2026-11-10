package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.AnalysisResponseDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * The analysis backend is a configurable, secure HTTPS endpoint. The absolute
 * request URL is provided per-call so the pharmacist can change the backend
 * from Settings without rebuilding the app.
 */
interface AnalysisApi {

    @Multipart
    @POST("analyze")
    suspend fun analyze(
        @Url url: String,
        @Part image: MultipartBody.Part,
        @Header("Authorization") authorization: String? = null
    ): Response<AnalysisResponseDto>
}

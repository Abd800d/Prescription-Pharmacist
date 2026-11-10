package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatRequestDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.ChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/** Chat with the AI pharmacist assistant via the secure backend. */
interface AssistantApi {

    @POST("chat")
    suspend fun chat(
        @Url url: String,
        @Body body: ChatRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<ChatResponseDto>
}

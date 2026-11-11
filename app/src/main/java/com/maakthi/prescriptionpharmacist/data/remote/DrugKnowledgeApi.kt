package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.DrugKnowledgeQueryDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.DrugKnowledgeResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Backend drug-information endpoint.
 *
 * The server (server/index.js `druginfo`) holds every authority API key in
 * Firebase secrets and proxies the official sources — the Android app never
 * contains any API key. Used by `ServerBackedKnowledgeProvider`.
 */
interface DrugKnowledgeApi {

    @POST
    suspend fun search(
        @Url url: String,
        @Body body: DrugKnowledgeQueryDto,
        @Header("Authorization") authorization: String? = null
    ): Response<DrugKnowledgeResponseDto>
}

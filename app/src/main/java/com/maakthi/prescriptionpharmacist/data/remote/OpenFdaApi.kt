package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.OpenFdaResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Public FDA drug label API (https://open.fda.gov) — no API key required.
 */
interface OpenFdaApi {

    @GET("drug/label.json")
    suspend fun searchDrugLabels(
        @Query("search") search: String,
        @Query("limit") limit: Int = 10
    ): Response<OpenFdaResponse>
}

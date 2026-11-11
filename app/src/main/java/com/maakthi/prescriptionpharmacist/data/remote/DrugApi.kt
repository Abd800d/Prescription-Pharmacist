package com.maakthi.prescriptionpharmacist.data.remote

import com.maakthi.prescriptionpharmacist.data.remote.dto.DrugDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * CRUD API of the shared drug database backend.
 *
 * Contract (all paths relative to the configured backend base URL):
 * - `GET    /drugs?q=&dosageForm=&manufacturer=&verified=` — search/filter
 * - `GET    /drugs/{id}`   — fetch one record
 * - `POST   /drugs`        — create (409 on duplicate barcode)
 * - `PUT    /drugs/{id}`   — full update
 * - `DELETE /drugs/{id}`   — remove
 *
 * The absolute request URL is provided per-call (`@Url`), exactly like
 * [AnalysisApi], so the backend can be changed from Settings. All calls
 * send the Firebase ID token as a Bearer header when available.
 */
interface DrugApi {

    @GET
    suspend fun list(
        @Url url: String,
        @Query("q") query: String? = null,
        @Query("dosageForm") dosageForm: String? = null,
        @Query("manufacturer") manufacturer: String? = null,
        @Query("verified") verified: Boolean? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<List<DrugDto>>

    @GET
    suspend fun get(
        @Url url: String,
        @Header("Authorization") authorization: String? = null
    ): Response<DrugDto>

    @POST
    suspend fun create(
        @Url url: String,
        @Body drug: DrugDto,
        @Header("Authorization") authorization: String? = null
    ): Response<DrugDto>

    @PUT
    suspend fun update(
        @Url url: String,
        @Body drug: DrugDto,
        @Header("Authorization") authorization: String? = null
    ): Response<DrugDto>

    @DELETE
    suspend fun delete(
        @Url url: String,
        @Header("Authorization") authorization: String? = null
    ): Response<Unit>
}

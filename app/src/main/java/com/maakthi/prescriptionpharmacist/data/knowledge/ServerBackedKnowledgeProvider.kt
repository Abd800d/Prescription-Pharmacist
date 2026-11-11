package com.maakthi.prescriptionpharmacist.data.knowledge

import com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeProvider
import com.maakthi.prescriptionpharmacist.data.analysis.BackendConfig
import com.maakthi.prescriptionpharmacist.data.remote.DrugKnowledgeApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.DrugKnowledgeQueryDto
import com.maakthi.prescriptionpharmacist.data.remote.dto.DrugKnowledgeFactDto
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connector for official authority APIs (ministry of health, local drug
 * authority…) through the app's secure backend.
 *
 * The backend (`server/index.js` → `druginfo`) owns every API key (Firebase
 * secrets) and returns facts already attributed to their official source.
 * When no authority API is configured yet, the endpoint answers 501 and
 * this connector reports itself as unavailable — the design leaves the seam
 * ready for future national sources.
 */
@Singleton
class ServerBackedKnowledgeProvider @Inject constructor(
    private val api: DrugKnowledgeApi,
    private val backendConfig: BackendConfig
) : DrugKnowledgeProvider {

    override val id: String = "authority"
    override val displayName: String = "Official authority APIs (secure backend)"
    override val sourceUrl: String = "https://github.com/Abd800d/Prescription-Pharmacist"
    override val isOfficial: Boolean = true

    override suspend fun fetchFacts(query: String): AppResult<List<DrugFact>> {
        val clean = query.trim()
        if (clean.isEmpty()) return AppResult.success(emptyList())
        return try {
            val base = backendConfig.baseUrl().trimEnd('/')
            val response = api.search(
                url = "$base/druginfo/search",
                body = DrugKnowledgeQueryDto(clean),
                authorization = backendConfig.authorization()
            )
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    AppResult.success(body?.facts.orEmpty().map { it.toFact() })
                }
                // Endpoint not deployed / no authority source configured yet —
                // the connector exists but has nothing to report. Real network
                // failures surface through exceptions below and warn the user.
                response.code() == 501 || response.code() == 404 ->
                    AppResult.success(emptyList())
                else -> AppResult.error(ErrorCode.NETWORK, "http ${response.code()}")
            }
        } catch (e: Exception) {
            AppResult.error(ErrorCode.NETWORK, e.message, e)
        }
    }

    private fun DrugKnowledgeFactDto.toFact(): DrugFact = DrugFact(
        field = field,
        value = value,
        source = DrugSourceInfo(
            id = "$id:${sourceName.hashCode()}",
            name = sourceName,
            url = sourceUrl,
            retrievedAt = retrievedAt,
            isOfficial = true
        )
    )
}

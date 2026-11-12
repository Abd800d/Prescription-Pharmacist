package com.maakthi.prescriptionpharmacist.data.knowledge

import com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeProvider
import com.maakthi.prescriptionpharmacist.data.remote.OpenFdaApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.OpenFdaDrug
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OPTIONAL connector to openFDA (US FDA public drug labels) — no API key
 * required, which is why it may be called directly from the app. Every
 * keyed source must go through the backend instead (see
 * [ServerBackedKnowledgeProvider]).
 *
 * https://open.fda.gov/apis/drug/label/
 */
@Singleton
class OpenFdaKnowledgeProvider @Inject constructor(
    private val openFdaApi: OpenFdaApi
) : DrugKnowledgeProvider {

    override val id: String = "openfda"
    override val displayName: String = "openFDA (US FDA)"
    override val sourceUrl: String = "https://open.fda.gov/apis/drug/label/"
    override val isOfficial: Boolean = true

    override suspend fun fetchFacts(query: String): AppResult<List<DrugFact>> {
        val clean = query.trim().replace("\"", "")
        if (clean.isEmpty()) return AppResult.success(emptyList())
        val search = """(openfda.brand_name:"$clean" OR openfda.generic_name:"$clean")"""
        return try {
            val response = openFdaApi.searchDrugLabels(search = search, limit = 1)
            when {
                !response.isSuccessful -> {
                    // openFDA answers 404 when nothing matches.
                    if (response.code() == 404) AppResult.success(emptyList())
                    else AppResult.error(com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.NETWORK, "http ${response.code()}")
                }
                else -> {
                    val drug = response.body()?.results?.firstOrNull()
                    AppResult.success(drug?.toFacts() ?: emptyList())
                }
            }
        } catch (e: Exception) {
            AppResult.error(com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.NETWORK, e.message, e)
        }
    }

    private fun OpenFdaDrug.toFacts(): List<DrugFact> {
        val source = DrugSourceInfo(
            id = id,
            name = displayName,
            url = sourceUrl,
            retrievedAt = System.currentTimeMillis(),
            isOfficial = true
        )
        fun fact(field: String, value: String?): DrugFact? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { DrugFact(field, it.take(900), source) }

        return listOfNotNull(
            fact("tradeName", openfda?.brandName?.firstOrNull()),
            fact("genericName", openfda?.genericName?.firstOrNull()),
            fact("manufacturer", openfda?.manufacturerName?.firstOrNull()),
            fact("dosageForm", openfda?.route?.firstOrNull()),
            fact("indications", indicationsAndUsage.firstOrNull()),
            fact("dosage", dosageAndAdministration.firstOrNull()),
            fact("warnings", warnings.firstOrNull()),
            fact("description", description.firstOrNull())
        )
    }
}

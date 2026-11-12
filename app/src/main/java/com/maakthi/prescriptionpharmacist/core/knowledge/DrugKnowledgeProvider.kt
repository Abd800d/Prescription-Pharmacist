package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo

/**
 * A pluggable connector to one official drug-information source.
 *
 * Implementations: `OpenFdaKnowledgeProvider` (optional, keyless) and
 * `ServerBackedKnowledgeProvider` (any keyed authority API — the key lives
 * on the backend, never in the app). New sources (ministry of health, local
 * drug authority…) only need a new implementation registered in DI.
 */
interface DrugKnowledgeProvider {

    val id: String
    val displayName: String
    /** Link to the source or its API documentation. */
    val sourceUrl: String
    val isOfficial: Boolean get() = true

    /** Optional connectors can be switched off. */
    val enabled: Boolean get() = true

    /** Fetches facts for one query. Failures return [AppResult.Error] — never throw. */
    suspend fun fetchFacts(query: String): AppResult<List<DrugFact>>

    fun asSource(retrievedAt: Long): DrugSourceInfo = DrugSourceInfo(
        id = id,
        name = displayName,
        url = sourceUrl,
        retrievedAt = retrievedAt,
        isOfficial = isOfficial
    )
}

/** Outcome of one provider call, with its source identity frozen at fetch time. */
data class ProviderOutcome(
    val source: DrugSourceInfo,
    val result: AppResult<List<DrugFact>>
)

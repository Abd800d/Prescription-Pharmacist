package com.maakthi.prescriptionpharmacist.data.knowledge

import com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeProvider
import com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeService
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import com.maakthi.prescriptionpharmacist.domain.repository.DrugKnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [DrugKnowledgeService]. Search results live only in
 * memory (UI state) — the approved drug database is written exclusively
 * through the pharmacist-reviewed add/update flow.
 */
@Singleton
class DrugKnowledgeRepositoryImpl @Inject constructor(
    private val service: DrugKnowledgeService
) : DrugKnowledgeRepository {

    /** Last fetch time per source, shown in the UI ("updated …"). */
    private val lastFetch = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun availableSources(): List<DrugSourceInfo> = service.availableSources()

    override suspend fun search(query: String): DrugKnowledgeResult {
        val result = service.search(query)
        val now = System.currentTimeMillis()
        lastFetch.update { current ->
            val fetched = result.facts.associate { it.source.id to it.source.retrievedAt } +
                result.failedSources.associate { it.id to now }
            current + fetched
        }
        return result
    }
}

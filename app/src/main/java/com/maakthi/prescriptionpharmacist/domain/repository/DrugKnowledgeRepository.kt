package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo

/**
 * Drug-knowledge search over official sources (openFDA, authority APIs…).
 *
 * The search layer NEVER writes to the approved drug database: results are
 * reference information that must be reviewed by the pharmacist before any
 * record is created or updated. General web search (Google…) is not used as
 * a medical source.
 */
interface DrugKnowledgeRepository {

    /** Registered official sources (name + link + update info shown in UI). */
    fun availableSources(): List<DrugSourceInfo>

    /** Aggregated facts with attribution; conflicts are flagged, never auto-resolved. */
    suspend fun search(query: String): DrugKnowledgeResult
}

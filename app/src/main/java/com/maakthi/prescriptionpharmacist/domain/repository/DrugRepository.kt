package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import kotlinx.coroutines.flow.Flow

/**
 * Local drug catalog (Room) plus remote lookup (openFDA) for details.
 */
interface DrugRepository {
    fun observeDrugs(): Flow<List<Drug>>
    suspend fun getDrug(id: String): Drug?
    suspend fun searchLocal(query: String): List<Drug>
    suspend fun upsertDrug(drug: Drug)
    suspend fun deleteDrug(id: String)
    /** Searches the public openFDA drug label database (no API key required). */
    suspend fun searchRemote(query: String): List<Drug>
}

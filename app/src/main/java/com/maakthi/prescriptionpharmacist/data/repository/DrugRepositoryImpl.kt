package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.data.remote.OpenFdaApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.OpenFdaDrug
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugRepositoryImpl @Inject constructor(
    private val dao: DrugDao,
    private val openFdaApi: OpenFdaApi
) : DrugRepository {

    override fun observeDrugs(): Flow<List<Drug>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getDrug(id: String): Drug? = dao.getById(id)?.toDomain()

    override suspend fun searchLocal(query: String): List<Drug> =
        dao.search(query).map { it.toDomain() }

    override suspend fun upsertDrug(drug: Drug) {
        dao.upsert(drug.toEntity())
    }

    override suspend fun deleteDrug(id: String) {
        dao.delete(id)
    }

    override suspend fun searchRemote(query: String): List<Drug> {
        val clean = query.trim().replace("\"", "")
        if (clean.isEmpty()) return emptyList()
        val search = """(openfda.brand_name:"$clean" OR openfda.generic_name:"$clean")"""
        return try {
            val response = openFdaApi.searchDrugLabels(search = search)
            if (!response.isSuccessful) {
                // openFDA answers 404 when nothing matches — treat as empty.
                emptyList()
            } else {
                response.body()?.results?.mapNotNull { it.toDrug() } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun OpenFdaDrug.toDrug(): Drug? {
        val brand = openfda?.brandName?.firstOrNull()
        val generic = openfda?.genericName?.firstOrNull()
        val name = brand ?: generic ?: return null
        return Drug(
            id = "fda-" + UUID.nameUUIDFromBytes(name.toByteArray()).toString(),
            name = name,
            scientificName = generic?.takeIf { it != name },
            type = when {
                brand != null -> MedicationType.BRAND
                generic != null -> MedicationType.GENERIC
                else -> MedicationType.UNKNOWN
            },
            manufacturer = openfda?.manufacturerName?.firstOrNull(),
            indications = indicationsAndUsage.firstOrNull()?.take(600),
            dosage = dosageAndAdministration.firstOrNull()?.take(600),
            warnings = warnings.firstOrNull()?.take(600),
            notes = description.firstOrNull()?.take(600),
            isLocal = false
        )
    }
}

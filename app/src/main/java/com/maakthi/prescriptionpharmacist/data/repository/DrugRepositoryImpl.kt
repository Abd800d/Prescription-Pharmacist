package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.analysis.BackendConfig
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugAuditDao
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.data.remote.DrugApi
import com.maakthi.prescriptionpharmacist.data.remote.OpenFdaApi
import com.maakthi.prescriptionpharmacist.data.remote.dto.OpenFdaDrug
import com.maakthi.prescriptionpharmacist.data.remote.dto.toDomain
import com.maakthi.prescriptionpharmacist.data.remote.dto.toDto
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.VerificationFilter
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import com.maakthi.prescriptionpharmacist.domain.usecase.DrugEntryValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local drug database (Room, indexed) + remote lookup (openFDA) + remote
 * CRUD API. All writes go through the Room tables; the audit table records
 * the change history. Duplicate detection is done on normalized keys.
 */
@Singleton
class DrugRepositoryImpl @Inject constructor(
    private val dao: DrugDao,
    private val auditDao: DrugAuditDao,
    private val openFdaApi: OpenFdaApi,
    private val drugApi: DrugApi,
    private val backendConfig: BackendConfig
) : DrugEntryRepository {

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

    // ---------- editable drug database ----------

    override suspend fun searchDrugs(query: String, filters: DrugFilters): List<Drug> =
        dao.searchFiltered(
            query = query,
            dosageForm = filters.dosageForm.orEmpty(),
            manufacturer = filters.manufacturer.orEmpty(),
            verified = when (filters.verification) {
                VerificationFilter.ALL -> null
                VerificationFilter.VERIFIED -> 1
                VerificationFilter.MANUAL -> 0
            }
        ).map { it.toDomain() }

    override suspend fun getDrugByBarcode(barcode: String): Drug? =
        dao.getByBarcode(barcode.trim())?.toDomain()

    override suspend fun findDuplicate(
        name: String,
        strength: String?,
        manufacturer: String?,
        excludeId: String?
    ): Drug? {
        if (name.isBlank()) return null
        val key = DrugEntryValidator.duplicateKey(name, strength, manufacturer)
        return dao.search(name)
            .map { it.toDomain() }
            .firstOrNull { drug ->
                drug.id != excludeId &&
                    DrugEntryValidator.duplicateKey(
                        DrugEntryValidator.primaryName(drug),
                        drug.strength,
                        drug.manufacturer
                    ) == key
            }
    }

    override suspend fun recordAudit(entry: DrugAuditEntry) {
        auditDao.insert(entry.toEntity())
    }

    override fun observeAudit(drugId: String): Flow<List<DrugAuditEntry>> =
        auditDao.observeByDrug(drugId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAudit(drugId: String): List<DrugAuditEntry> =
        auditDao.byDrug(drugId).map { it.toDomain() }

    override suspend fun filterOptions(): DrugFilterOptions = DrugFilterOptions(
        dosageForms = dao.distinctDosageForms(),
        manufacturers = dao.distinctManufacturers()
    )

    override suspend fun searchApi(query: String): List<Drug> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        return try {
            val base = backendConfig.baseUrl().trimEnd('/')
            val response = drugApi.list(
                url = "$base/drugs",
                query = clean,
                authorization = backendConfig.authorization()
            )
            if (response.isSuccessful) {
                response.body()?.map { it.toDomain() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- openFDA enrichment ----------

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
            isLocal = false,
            tradeNameEnglish = brand,
            genericNameEnglish = generic,
            dosageForm = openfda?.route?.firstOrNull(),
            sourceName = "openFDA",
            sourceUrl = "https://api.fda.gov/drug/label.json",
            sourceDate = null,
            isVerified = true
        )
    }
}

package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import kotlinx.coroutines.flow.Flow

/**
 * The editable drug database on top of the analysis catalog repository.
 *
 * Adds indexed multi-field search (trade/generic names in both languages,
 * active ingredient, barcode), result filters, duplicate detection, the
 * change-history (audit) log, and a remote CRUD API.
 */
interface DrugEntryRepository : DrugRepository {

    /** Search by trade/generic name (AR+EN), active ingredient or barcode. */
    suspend fun searchDrugs(query: String, filters: DrugFilters = DrugFilters()): List<Drug>

    suspend fun getDrugByBarcode(barcode: String): Drug?

    /**
     * Returns a record that would duplicate [name]/[strength]/[manufacturer],
     * ignoring [excludeId]. Match is on normalized names.
     */
    suspend fun findDuplicate(
        name: String,
        strength: String?,
        manufacturer: String?,
        excludeId: String?
    ): Drug?

    /** Persists one audit entry (change history). */
    suspend fun recordAudit(entry: DrugAuditEntry)

    fun observeAudit(drugId: String): Flow<List<DrugAuditEntry>>

    suspend fun getAudit(drugId: String): List<DrugAuditEntry>

    /** Distinct dosage forms / manufacturers offered by the search filters. */
    suspend fun filterOptions(): DrugFilterOptions

    /** Remote drug API lookup (falls back to empty on failure). */
    suspend fun searchApi(query: String): List<Drug>
}

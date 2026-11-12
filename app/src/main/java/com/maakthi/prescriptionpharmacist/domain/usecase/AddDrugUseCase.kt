package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Adds a drug to the database after validation and duplicate prevention.
 *
 * Duplicates are rejected when another record shares the barcode, or shares
 * the normalized name + strength + manufacturer. Every successful add writes
 * an ADD entry to the change history.
 */
class AddDrugUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(drug: Drug, performedBy: String): AppResult<Drug> {
        val error = DrugEntryValidator.validate(drug)
        if (error != null) return AppResult.error(error.first, error.second)

        val byBarcode = drug.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            drugs.getDrugByBarcode(it)
        }
        if (byBarcode != null && byBarcode.id != drug.id) {
            return AppResult.error(
                com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.DUPLICATE,
                "barcode=${drug.barcode}"
            )
        }
        val duplicate = drugs.findDuplicate(
            name = DrugEntryValidator.primaryName(drug),
            strength = drug.strength,
            manufacturer = drug.manufacturer,
            excludeId = drug.id
        )
        if (duplicate != null) {
            return AppResult.error(
                com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.DUPLICATE,
                "id=${duplicate.id}"
            )
        }

        val saved = drug.copy(updatedAt = System.currentTimeMillis(), createdBy = drug.createdBy ?: performedBy)
        drugs.upsertDrug(saved)
        drugs.recordAudit(
            DrugAuditEntry(
                id = UUID.randomUUID().toString(),
                drugId = saved.id,
                action = DrugAuditAction.ADD,
                changes = DrugEntryValidator.primaryName(saved),
                performedBy = performedBy,
                timestamp = saved.updatedAt
            )
        )
        return AppResult.success(saved)
    }
}

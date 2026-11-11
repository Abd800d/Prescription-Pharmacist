package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.drugChanges
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Updates a drug and records the field-level diff in the change history.
 * Duplicate prevention runs against every OTHER record.
 */
class UpdateDrugUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(drug: Drug, performedBy: String): AppResult<Drug> {
        val existing = drugs.getDrug(drug.id)
            ?: return AppResult.error(ErrorCode.NOT_FOUND, drug.id)

        val error = DrugEntryValidator.validate(drug)
        if (error != null) return AppResult.error(error.first, error.second)

        val byBarcode = drug.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            drugs.getDrugByBarcode(it)
        }
        if (byBarcode != null && byBarcode.id != drug.id) {
            return AppResult.error(ErrorCode.DUPLICATE, "barcode=${drug.barcode}")
        }
        val duplicate = drugs.findDuplicate(
            name = DrugEntryValidator.primaryName(drug),
            strength = drug.strength,
            manufacturer = drug.manufacturer,
            excludeId = drug.id
        )
        if (duplicate != null) {
            return AppResult.error(ErrorCode.DUPLICATE, "id=${duplicate.id}")
        }

        val saved = drug.copy(updatedAt = System.currentTimeMillis())
        drugs.upsertDrug(saved)
        drugs.recordAudit(
            DrugAuditEntry(
                id = UUID.randomUUID().toString(),
                drugId = saved.id,
                action = DrugAuditAction.UPDATE,
                changes = drugChanges(existing, saved).ifEmpty { "-" },
                performedBy = performedBy,
                timestamp = saved.updatedAt
            )
        )
        return AppResult.success(saved)
    }
}

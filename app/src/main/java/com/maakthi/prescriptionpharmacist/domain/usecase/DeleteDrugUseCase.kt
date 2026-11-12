package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Deletes a drug and records the removal in the change history.
 * The UI must ask for confirmation before invoking this use case.
 */
class DeleteDrugUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(id: String, performedBy: String): AppResult<Unit> {
        val existing = drugs.getDrug(id)
            ?: return AppResult.error(ErrorCode.NOT_FOUND, id)
        drugs.deleteDrug(id)
        drugs.recordAudit(
            DrugAuditEntry(
                id = UUID.randomUUID().toString(),
                drugId = id,
                action = DrugAuditAction.DELETE,
                changes = DrugEntryValidator.primaryName(existing),
                performedBy = performedBy,
                timestamp = System.currentTimeMillis()
            )
        )
        return AppResult.success(Unit)
    }
}

package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Change history of one drug (newest first). */
class GetDrugAuditUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(drugId: String): List<DrugAuditEntry> = drugs.getAudit(drugId)
    fun observe(drugId: String): Flow<List<DrugAuditEntry>> = drugs.observeAudit(drugId)
}

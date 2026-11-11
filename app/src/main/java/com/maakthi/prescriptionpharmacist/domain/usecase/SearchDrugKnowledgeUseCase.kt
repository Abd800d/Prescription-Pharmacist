package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.DrugKnowledgeRepository
import javax.inject.Inject

/**
 * Searches the official drug-information sources.
 *
 * - blank queries are rejected ([ErrorCode.VALIDATION]);
 * - offline/timeout conditions are reported inside the result
 *   ([com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning]), not thrown;
 * - nothing is written to the drug database — saving requires pharmacist
 *   review through the normal add/update flow.
 */
class SearchDrugKnowledgeUseCase @Inject constructor(
    private val knowledge: DrugKnowledgeRepository
) {
    suspend operator fun invoke(query: String): AppResult<DrugKnowledgeResult> {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return AppResult.error(ErrorCode.VALIDATION, "query_required")
        }
        return AppResult.success(knowledge.search(clean))
    }
}

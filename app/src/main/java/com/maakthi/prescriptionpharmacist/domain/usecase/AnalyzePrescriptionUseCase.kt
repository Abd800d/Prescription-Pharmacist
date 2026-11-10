package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AnalysisRepository
import javax.inject.Inject

/**
 * Analyzes a prescription photo with the secure AI backend and returns the
 * extracted medications. The result always requires pharmacist confirmation.
 */
class AnalyzePrescriptionUseCase @Inject constructor(
    private val analysisRepository: AnalysisRepository
) {
    suspend operator fun invoke(imageBytes: ByteArray): AppResult<AnalysisResult> {
        if (imageBytes.isEmpty()) {
            return AppResult.error(ErrorCode.IMAGE_UNREADABLE)
        }
        return analysisRepository.analyzeImage(imageBytes)
    }
}

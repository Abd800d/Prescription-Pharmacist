package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult

/**
 * Sends a prescription photo to the secure AI backend and returns the
 * extracted medications with confidence scores.
 */
interface AnalysisRepository {
    suspend fun analyzeImage(imageBytes: ByteArray): AppResult<AnalysisResult>
}

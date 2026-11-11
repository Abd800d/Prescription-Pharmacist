package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.core.analysis.AiExtractionEngine
import com.maakthi.prescriptionpharmacist.core.analysis.AiMedicationHint
import com.maakthi.prescriptionpharmacist.core.analysis.AnalysisPipeline
import com.maakthi.prescriptionpharmacist.core.analysis.LocalRuleBasedExtractionEngine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrEngine
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.MedicationAlternative
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Runs the staged prescription analysis:
 *
 * 1–2. image (already enhanced by the capture pipeline)
 * 3.   OCR via the pluggable [OcrEngine]
 * 4–8. segmentation + field extraction (rules) + AI hints via the pluggable
 *      [AiExtractionEngine], with the local rule engine as offline fallback
 * 9.   matching against the drug catalog (+ openFDA enrichment)
 * 10.  confidence scoring
 * 11.  alternatives for unclear readings
 * 12.  the result ALWAYS requires pharmacist confirmation — nothing is ever
 *      adopted or dispensed automatically.
 */
class AnalyzePrescriptionUseCase @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val aiExtractionEngine: AiExtractionEngine,
    private val drugRepository: DrugRepository
) {

    private val pipeline = AnalysisPipeline()

    /** Remote lookups are capped to keep analysis fast and polite. */
    private val maxRemoteLookups = 3

    suspend operator fun invoke(imageBytes: ByteArray): AppResult<AnalysisResult> {
        if (imageBytes.isEmpty()) {
            return AppResult.error(ErrorCode.IMAGE_UNREADABLE)
        }

        // Stage 3: OCR.
        val ocr = when (val result = ocrEngine.recognize(imageBytes)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        // Stages 5–8: AI hints, falling back to local rules when the provider
        // is unavailable — extraction never blocks the analysis.
        val hints: List<AiMedicationHint> = when (val result = aiExtractionEngine.extract(ocr.fullText)) {
            is AppResult.Success -> result.data
            is AppResult.Error -> (LocalRuleBasedExtractionEngine().extract(ocr.fullText)).getOrNull()
                ?: emptyList()
        }

        // Stage 9 input: the local drug catalog.
        val catalog: List<Drug> = runCatching { drugRepository.observeDrugs().first() }
            .getOrDefault(emptyList())

        // Stages 4–11.
        val analysis = pipeline.analyze(ocr, hints, catalog).copy(requestId = ocr.requestId)

        // Stage 9 (continued): openFDA enrichment for unmatched, uncertain
        // names — suggestions only, never presented as certain matches.
        return AppResult.success(enrichFromRemote(analysis))
    }

    private suspend fun enrichFromRemote(analysis: AnalysisResult): AnalysisResult {
        var lookups = 0
        val medications = analysis.medications.map { candidate ->
            if (!candidate.isUncertain || candidate.alternatives.isNotEmpty() ||
                lookups >= maxRemoteLookups
            ) {
                return@map candidate
            }
            lookups++
            val remote = runCatching { drugRepository.searchRemote(candidate.name) }
                .getOrDefault(emptyList())
                .take(2)
            if (remote.isEmpty()) {
                candidate
            } else {
                candidate.copy(
                    alternatives = (candidate.alternatives + remote.map {
                        MedicationAlternative(
                            name = it.name,
                            scientificName = it.scientificName,
                            score = 0.6f
                        )
                    }).distinctBy { it.name.lowercase() }.take(4)
                )
            }
        }
        return analysis.copy(medications = medications)
    }
}

package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AppResult

/**
 * Real offline [AiExtractionEngine]: turns OCR text into medication hints
 * using only local rules ([PrescriptionSegmenter] + [MedicationLineParser]).
 * Used as the fallback when the remote AI provider is unavailable.
 */
class LocalRuleBasedExtractionEngine : AiExtractionEngine {

    override suspend fun extract(ocrText: String): AppResult<List<AiMedicationHint>> {
        val lines = PrescriptionSegmenter.segment(ocrText)
        val hints = lines.mapNotNull { line ->
            val parsed = MedicationLineParser.parse(line) ?: return@mapNotNull null
            AiMedicationHint(
                name = parsed.name ?: line.text,
                scientificName = null,
                type = null,
                strength = parsed.strength,
                form = parsed.form,
                dose = parsed.dose,
                route = parsed.route,
                duration = parsed.duration,
                rawText = parsed.rawText,
                unclear = MedicationLineParser.isUnclearName(parsed.name ?: line.text),
                alternatives = emptyList()
            )
        }
        return AppResult.success(hints)
    }
}

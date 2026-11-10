package com.maakthi.prescriptionpharmacist.domain.model

/**
 * Result of the AI extraction for a single prescription image.
 * All fields come from the analysis service — the pharmacist confirms
 * them before anything is adopted.
 */
data class AnalysisResult(
    val medications: List<MedicationCandidate>,
    val language: String? = null,
    val notes: String? = null,
    val requestId: String? = null
)

/**
 * A medication extracted by the AI from the prescription image.
 *
 * @param confidence model confidence in the range 0f..1f.
 */
data class MedicationCandidate(
    val name: String,
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val confidence: Float = 0f,
    val rawText: String? = null
) {
    val confidenceLevel: ConfidenceLevel
        get() = confidenceLevelOf(confidence)

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f
    }
}

enum class MedicationType { BRAND, GENERIC, UNKNOWN }

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

/** Maps a raw 0..1 confidence to a display tier. */
fun confidenceLevelOf(confidence: Float): ConfidenceLevel = when {
    confidence >= MedicationCandidate.HIGH_CONFIDENCE_THRESHOLD -> ConfidenceLevel.HIGH
    confidence >= MedicationCandidate.MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceLevel.MEDIUM
    else -> ConfidenceLevel.LOW
}

/** Parses a type string coming from the analysis service. */
fun medicationTypeOf(raw: String?): MedicationType = when (raw?.trim()?.uppercase()) {
    "BRAND", "TRADE" -> MedicationType.BRAND
    "GENERIC", "SCIENTIFIC" -> MedicationType.GENERIC
    else -> MedicationType.UNKNOWN
}

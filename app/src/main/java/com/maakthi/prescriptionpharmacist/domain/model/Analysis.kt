package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * Result of the multi-stage prescription analysis pipeline.
 * All fields come from OCR + extraction + catalog matching — the pharmacist
 * confirms them before anything is adopted. Nothing is ever dispensed
 * automatically.
 */
@Serializable
data class AnalysisResult(
    val medications: List<MedicationCandidate>,
    val language: String? = null,
    val notes: String? = null,
    val requestId: String? = null,
    /** Raw OCR text the extraction ran on (kept for audit/review). */
    val ocrText: String? = null,
    /** Non-localized warning codes shown by the UI. */
    val warnings: List<AnalysisWarning> = emptyList(),
    /**
     * Always true: results are never adopted without explicit pharmacist
     * confirmation.
     */
    val requiresPharmacistConfirmation: Boolean = true
)

/**
 * A medication extracted from the prescription image.
 *
 * @param confidence composite confidence in the range 0f..1f.
 * @param isUncertain true when the result must be displayed as
 * "النتيجة غير مؤكدة" (uncertain) — low confidence, no catalog match, or
 * ambiguous handwriting.
 * @param alternatives plausible catalog entries when the reading is unclear.
 */
@Serializable
data class MedicationCandidate(
    val name: String,
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val confidence: Float = 0f,
    val rawText: String? = null,
    val isUncertain: Boolean = false,
    val alternatives: List<MedicationAlternative> = emptyList(),
    /** Set by the pipeline once the pharmacist adopts the result. */
    val confirmedByPharmacist: Boolean = false
) {
    val confidenceLevel: ConfidenceLevel
        get() = confidenceLevelOf(confidence)

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f
    }
}

/** A plausible alternative reading for an uncertain medication name. */
@Serializable
data class MedicationAlternative(
    val name: String,
    val scientificName: String? = null,
    val score: Float = 0f
)

@Serializable
enum class MedicationType { BRAND, GENERIC, UNKNOWN }

@Serializable
enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

/** Machine-readable warnings carried with every analysis. */
@Serializable
enum class AnalysisWarning {
    /** Handwriting recognition may be inaccurate. */
    HANDWRITING_MAY_BE_INACCURATE,
    /** The app never dispenses medication automatically. */
    NO_AUTOMATIC_DISPENSING,
    /** One or more results are uncertain and need extra scrutiny. */
    UNCERTAIN_RESULTS_PRESENT
}

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

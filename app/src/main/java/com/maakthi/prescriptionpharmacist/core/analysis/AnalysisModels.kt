package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Provider-independent OCR models (stage 3 of the pipeline).
 */
data class OcrLine(
    val text: String,
    /** Per-line recognition confidence 0..1; 1f when the provider reports none. */
    val confidence: Float = 1f
)

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine> = emptyList(),
    val language: String? = null,
    val requestId: String? = null
)

/**
 * Provider-independent AI extraction output (stages 5–8): one hint per
 * suspected medication. Hints are only suggestions — every field must be
 * grounded in the OCR text before it is kept (see [GroundingFilter]).
 */
data class AiMedicationHint(
    val name: String,
    val scientificName: String? = null,
    /** "BRAND", "GENERIC" or null when unknown. */
    val type: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val rawText: String? = null,
    /** True when the handwriting is ambiguous for this entry. */
    val unclear: Boolean = false,
    /** Alternative readings suggested for ambiguous names. */
    val alternatives: List<String> = emptyList()
)

/** One medication line after segmentation (stage 4). */
data class PrescriptionLine(
    val text: String,
    val ocrConfidence: Float,
    val index: Int
)

/** Rule-based parse of a single line (stages 5–8). */
data class ParsedMedication(
    val name: String?,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val rawText: String
)

/**
 * OCR layer interface. Implementations wrap any provider (server vision,
 * on-device engine, ...). Kept independent from the AI extraction layer so
 * either can be swapped without touching the pipeline.
 */
interface OcrEngine {
    suspend fun recognize(imageBytes: ByteArray): com.maakthi.prescriptionpharmacist.domain.model.AppResult<OcrResult>
}

/**
 * AI extraction layer interface: turns OCR text into structured medication
 * hints. Implementations can call a remote LLM, a local model, or pure rules
 * ([LocalRuleBasedExtractionEngine]) — the pipeline accepts any of them.
 */
interface AiExtractionEngine {
    suspend fun extract(ocrText: String): com.maakthi.prescriptionpharmacist.domain.model.AppResult<List<AiMedicationHint>>
}

package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Stage 10: combines OCR confidence, parse completeness and catalog match
 * quality into one 0..1 score. The scale is documented here so the UI can
 * explain it to the pharmacist.
 */
object ConfidenceScorer {

    /** Below this score the result must be displayed as uncertain. */
    const val UNCERTAIN_THRESHOLD = 0.5f

    fun score(
        ocrConfidence: Float,
        nameFound: Boolean,
        fieldsFound: Int,
        matchScore: Float?,
        hintUnclear: Boolean
    ): Float {
        if (!nameFound) return 0.25f
        val ocr = ocrConfidence.coerceIn(0f, 1f)
        val completeness = (fieldsFound.coerceIn(0, 4) / 4f)
        val unclearPenalty = if (hintUnclear) 0.75f else 1f

        val base = if (matchScore != null) {
            0.5f * matchScore.coerceIn(0f, 1f) + 0.3f * ocr + 0.2f * completeness
        } else {
            // An unverifiable name is capped below the MEDIUM tier.
            (0.35f * ocr + 0.15f * completeness).coerceAtMost(0.49f)
        }
        return (base * unclearPenalty).coerceIn(0f, 1f)
    }

    fun isUncertain(confidence: Float, matched: Boolean, hintUnclear: Boolean): Boolean =
        confidence < UNCERTAIN_THRESHOLD || !matched || hintUnclear
}

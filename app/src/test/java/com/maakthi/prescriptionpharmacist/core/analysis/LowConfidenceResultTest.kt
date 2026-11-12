package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Low-confidence prescription results: when OCR is weak, the name is
 * unverifiable, or the hint is unclear, the result MUST be marked uncertain
 * so nothing is presented to the patient as fact without pharmacist review.
 */
class LowConfidenceResultTest {

    @Test
    fun `low confidence score is uncertain`() {
        assertTrue(ConfidenceScorer.isUncertain(confidence = 0.3f, matched = true, hintUnclear = false))
    }

    @Test
    fun `unmatched drug is uncertain even with a high score`() {
        assertTrue(ConfidenceScorer.isUncertain(confidence = 0.9f, matched = false, hintUnclear = false))
    }

    @Test
    fun `unclear hint is uncertain even with a high score`() {
        assertTrue(ConfidenceScorer.isUncertain(confidence = 0.9f, matched = true, hintUnclear = true))
    }

    @Test
    fun `confident matched clear result is not uncertain`() {
        assertFalse(ConfidenceScorer.isUncertain(confidence = 0.8f, matched = true, hintUnclear = false))
    }

    @Test
    fun `low ocr confidence produces a low score`() {
        val high = ConfidenceScorer.score(0.95f, true, 4, 1f, false)
        val low = ConfidenceScorer.score(0.2f, true, 4, 1f, false)
        assertTrue(low < high)
        assertTrue("weak OCR must drag the score down", low < 0.85f)
    }

    @Test
    fun `unverifiable name is capped below the medium tier`() {
        val score = ConfidenceScorer.score(1f, true, 4, null, false)
        assertTrue("unverifiable name must stay below 0.5", score < 0.5f)
    }

    @Test
    fun `the uncertain threshold sits between the tiers`() {
        assertEquals(0.5f, ConfidenceScorer.UNCERTAIN_THRESHOLD, 0.0001f)
    }

    @Test
    fun `low-confidence end to end - weak ocr line stays uncertain`() = kotlinx.coroutines.test.runTest {
        val hints = LocalRuleBasedExtractionEngine().extract("Med1? 500 mg tablets").getOrNull().orEmpty()
        assertTrue(hints.isNotEmpty())
        val hint = hints.first()
        assertTrue("question-mark name must be flagged unclear", hint.unclear)
        val confidence = ConfidenceScorer.score(
            ocrConfidence = 0.3f,
            nameFound = true,
            fieldsFound = 1,
            matchScore = null,
            hintUnclear = hint.unclear
        )
        assertTrue(confidence < ConfidenceScorer.UNCERTAIN_THRESHOLD)
        assertTrue(ConfidenceScorer.isUncertain(confidence, matched = false, hintUnclear = hint.unclear))
    }
}

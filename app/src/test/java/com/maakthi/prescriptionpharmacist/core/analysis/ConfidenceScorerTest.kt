package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceScorerTest {

    @Test
    fun `full evidence produces high confidence`() {
        val score = ConfidenceScorer.score(
            ocrConfidence = 1f,
            nameFound = true,
            fieldsFound = 4,
            matchScore = 1f,
            hintUnclear = false
        )
        assertTrue("score=$score", score >= 0.8f)
    }

    @Test
    fun `missing name is always very low`() {
        val score = ConfidenceScorer.score(
            ocrConfidence = 1f,
            nameFound = false,
            fieldsFound = 4,
            matchScore = 1f,
            hintUnclear = false
        )
        assertTrue(score <= 0.25f)
    }

    @Test
    fun `no catalog match caps the score below medium`() {
        val score = ConfidenceScorer.score(
            ocrConfidence = 1f,
            nameFound = true,
            fieldsFound = 4,
            matchScore = null,
            hintUnclear = false
        )
        assertTrue("score=$score", score <= 0.49f)
    }

    @Test
    fun `unclear hints are penalized`() {
        val clear = ConfidenceScorer.score(1f, true, 3, 1f, hintUnclear = false)
        val unclear = ConfidenceScorer.score(1f, true, 3, 1f, hintUnclear = true)
        assertTrue("clear=$clear unclear=$unclear", unclear < clear)
    }

    @Test
    fun `low ocr confidence lowers the score`() {
        val high = ConfidenceScorer.score(0.9f, true, 3, 1f, false)
        val low = ConfidenceScorer.score(0.3f, true, 3, 1f, false)
        assertTrue(low < high)
    }

    @Test
    fun `uncertainty rules`() {
        assertTrue(ConfidenceScorer.isUncertain(0.4f, matched = true, hintUnclear = false))
        assertTrue(ConfidenceScorer.isUncertain(0.9f, matched = false, hintUnclear = false))
        assertTrue(ConfidenceScorer.isUncertain(0.9f, matched = true, hintUnclear = true))
        assertFalse(ConfidenceScorer.isUncertain(0.9f, matched = true, hintUnclear = false))
    }

    @Test
    fun `score stays in zero to one range`() {
        val score = ConfidenceScorer.score(2f, true, 99, 5f, false)
        assertEquals(1f, score.coerceIn(0f, 1f), 0.001f)
        val negative = ConfidenceScorer.score(-1f, true, -2, -3f, false)
        assertTrue(negative >= 0f)
    }
}

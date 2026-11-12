package com.maakthi.prescriptionpharmacist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisModelTest {

    @Test
    fun `confidence level maps by threshold`() {
        assertEquals(ConfidenceLevel.HIGH, candidate(0.95f).confidenceLevel)
        assertEquals(ConfidenceLevel.HIGH, candidate(0.8f).confidenceLevel)
        assertEquals(ConfidenceLevel.MEDIUM, candidate(0.79f).confidenceLevel)
        assertEquals(ConfidenceLevel.MEDIUM, candidate(0.5f).confidenceLevel)
        assertEquals(ConfidenceLevel.LOW, candidate(0.49f).confidenceLevel)
        assertEquals(ConfidenceLevel.LOW, candidate(0f).confidenceLevel)
    }

    @Test
    fun `medication type parsing is case insensitive`() {
        assertEquals(MedicationType.BRAND, medicationTypeOf("brand"))
        assertEquals(MedicationType.BRAND, medicationTypeOf("TRADE"))
        assertEquals(MedicationType.GENERIC, medicationTypeOf("Generic"))
        assertEquals(MedicationType.GENERIC, medicationTypeOf("scientific"))
        assertEquals(MedicationType.UNKNOWN, medicationTypeOf(null))
        assertEquals(MedicationType.UNKNOWN, medicationTypeOf("something-else"))
    }

    @Test
    fun `prescription status parsing falls back to pending`() {
        assertEquals(PrescriptionStatus.CONFIRMED, prescriptionStatusOfTest("CONFIRMED"))
        assertEquals(PrescriptionStatus.REJECTED, prescriptionStatusOfTest("REJECTED"))
        assertEquals(PrescriptionStatus.PENDING, prescriptionStatusOfTest("nope"))
        assertEquals(PrescriptionStatus.PENDING, prescriptionStatusOfTest(""))
    }

    private fun candidate(confidence: Float) = MedicationCandidate(
        name = "Panadol",
        confidence = confidence
    )
}

/** Mirrors the mapping used by the data layer. */
private fun prescriptionStatusOfTest(raw: String): PrescriptionStatus =
    runCatching { PrescriptionStatus.valueOf(raw) }.getOrDefault(PrescriptionStatus.PENDING)

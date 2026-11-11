package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AnalysisWarning
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end pipeline tests covering the required safety behavior:
 * unclear handwriting, similar names, no invention, pharmacist gating.
 */
class AnalysisPipelineTest {

    private val pipeline = AnalysisPipeline()

    private val catalog = listOf(
        Drug(id = "1", name = "Panadol", scientificName = "Paracetamol", type = MedicationType.BRAND),
        Drug(id = "2", name = "Panadol Extra", scientificName = "Paracetamol/Caffeine", type = MedicationType.BRAND),
        Drug(id = "3", name = "Panadeine", scientificName = "Paracetamol/Codeine", type = MedicationType.BRAND),
        Drug(id = "4", name = "Augmentin", scientificName = "Amoxicillin/Clavulanate", type = MedicationType.BRAND)
    )

    private fun ocr(vararg lines: String, confidences: List<Float> = emptyList()) = OcrResult(
        fullText = lines.joinToString("\n"),
        lines = lines.mapIndexed { i, t ->
            OcrLine(t, confidences.getOrElse(i) { 1f })
        },
        language = "mixed"
    )

    @Test
    fun `clear line yields high-confidence matched medication`() {
        val result = pipeline.analyze(
            ocr("Panadol 500 mg tablets twice daily for 5 days"),
            hints = emptyList(),
            catalog = catalog
        )
        assertEquals(1, result.medications.size)
        val med = result.medications[0]
        assertEquals("Panadol", med.name) // canonical catalog name
        assertEquals("Paracetamol", med.scientificName)
        assertEquals(MedicationType.BRAND, med.type)
        assertEquals("500 mg", med.strength)
        assertEquals("tablet", med.form)
        assertTrue("confidence=${med.confidence}", med.confidence >= 0.8f)
        assertFalse(med.isUncertain)
    }

    @Test
    fun `unclear handwriting is flagged uncertain with alternatives`() {
        val hints = listOf(
            AiMedicationHint(
                name = "Pana?ol",
                strength = "500 mg",
                rawText = "Pana?ol 500 mg",
                unclear = true,
                alternatives = listOf("Panadol", "Panadeine")
            )
        )
        val result = pipeline.analyze(
            ocr("Pana?ol 500 mg"),
            hints = hints,
            catalog = catalog
        )
        val med = result.medications[0]
        // The required on-screen state for low confidence:
        assertTrue("not uncertain", med.isUncertain)
        assertTrue(
            "alternatives=${med.alternatives}",
            med.alternatives.any { it.name == "Panadol" || it.name == "Panadeine" }
        )
        assertTrue(result.warnings.contains(AnalysisWarning.UNCERTAIN_RESULTS_PRESENT))
    }

    @Test
    fun `similar names keep the exact one and list siblings`() {
        val result = pipeline.analyze(
            ocr("Panadeine 500 mg"),
            hints = emptyList(),
            catalog = catalog
        )
        val med = result.medications[0]
        assertEquals("Panadeine", med.name)
        assertTrue(
            "alternatives=${med.alternatives}",
            med.alternatives.any { it.name == "Panadol" }
        )
    }

    @Test
    fun `never invents - unknown name is kept as the literal reading and uncertain`() {
        val result = pipeline.analyze(
            ocr("Xyzzqtrazine 900 mg"),
            hints = emptyList(),
            catalog = catalog
        )
        val med = result.medications[0]
        assertEquals("Xyzzqtrazine", med.name) // literal reading, not a made-up drug
        assertTrue(med.isUncertain)
        assertEquals("900 mg", med.strength) // literal span from the text
    }

    @Test
    fun `never invents - ungrounded hint fields are dropped`() {
        val hints = listOf(
            AiMedicationHint(
                name = "Panadol",
                strength = "1000 mg", // NOT in the OCR text
                dose = "1 tablet daily",
                rawText = "Panadol 500 mg"
            )
        )
        val result = pipeline.analyze(
            ocr("Panadol 500 mg"),
            hints = hints,
            catalog = catalog
        )
        val med = result.medications[0]
        assertEquals("500 mg", med.strength) // grounded value survives
        assertFalse("1000 mg" == med.strength) // invention dropped
    }

    @Test
    fun `multiple medications are split from one line`() {
        val result = pipeline.analyze(
            ocr("Panadol, Augmentin 1g"),
            hints = emptyList(),
            catalog = catalog
        )
        assertEquals(2, result.medications.size)
    }

    @Test
    fun `result always requires pharmacist confirmation and never starts confirmed`() {
        val result = pipeline.analyze(
            ocr("Panadol 500 mg"),
            hints = emptyList(),
            catalog = catalog
        )
        assertTrue(result.requiresPharmacistConfirmation)
        assertTrue(result.medications.all { !it.confirmedByPharmacist })
    }

    @Test
    fun `mandatory warnings are always present`() {
        val result = pipeline.analyze(
            ocr("Panadol 500 mg"),
            hints = emptyList(),
            catalog = catalog
        )
        assertTrue(result.warnings.contains(AnalysisWarning.HANDWRITING_MAY_BE_INACCURATE))
        assertTrue(result.warnings.contains(AnalysisWarning.NO_AUTOMATIC_DISPENSING))
    }

    @Test
    fun `ocr confidence feeds the final score`() {
        val result = pipeline.analyze(
            ocr("Panadol 500 mg", confidences = listOf(0.5f)),
            hints = emptyList(),
            catalog = catalog
        )
        val med = result.medications[0]
        assertTrue("confidence=${med.confidence}", med.confidence < 1f)
    }
}

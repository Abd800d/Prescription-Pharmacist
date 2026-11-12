package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline OCR-result extraction (Arabic + English): the local rule engine
 * never invents data — every field is a literal span of the input.
 */
class LocalRuleBasedExtractionEngineTest {

    private val engine = LocalRuleBasedExtractionEngine()

    @Test
    fun `english medication line is parsed into structured hints`() = runTest {
        val result = engine.extract("Panadol 500 mg tablets")
        assertTrue(result.isSuccess)
        val hint = result.getOrNull()!!.single()
        assertTrue(hint.name.contains("Panadol"))
        assertEquals("500 mg", hint.strength)
        assertEquals("tablet", hint.form)
        assertEquals("Panadol 500 mg tablets", hint.rawText)
        assertFalse(hint.unclear)
    }

    @Test
    fun `arabic medication line is parsed`() = runTest {
        val result = engine.extract("بنادول 500 مجم أقراص")
        assertTrue(result.isSuccess)
        val hint = result.getOrNull()!!.single()
        assertTrue(hint.name.contains("بنادول"))
        // Arabic units are canonicalized to the English unit
        assertEquals("500 mg", hint.strength)
        assertEquals("tablet", hint.form)
    }

    @Test
    fun `multi-line prescription produces one hint per medication line`() = runTest {
        val result = engine.extract(
            """
            Panadol 500 mg tablets
            Amoxicillin 250 mg/5 ml syrup
            """.trimIndent()
        )
        val hints = result.getOrNull().orEmpty()
        assertTrue(hints.size >= 2)
        assertTrue(hints.any { it.rawText?.contains("Amoxicillin") == true && it.form == "syrup" })
    }

    @Test
    fun `empty and garbage input degrade to empty results, never errors`() = runTest {
        for (input in listOf("", "   ", "\n\n", "123 456 !!!", "---- -----")) {
            val result = engine.extract(input)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull().orEmpty().isEmpty())
        }
    }

    @Test
    fun `ambiguous names are flagged unclear for pharmacist review`() = runTest {
        val result = engine.extract("؟؟؟ 500 mg")
        val hint = result.getOrNull()!!.single()
        assertTrue(hint.unclear)
    }

    @Test
    fun `nothing is invented — missing fields stay null`() = runTest {
        val hint = engine.extract("Augmentin").getOrNull()!!.single()
        assertEquals(null, hint.strength)
        assertEquals(null, hint.form)
        assertEquals(null, hint.dose)
    }
}

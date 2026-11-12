package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationLineParserTest {

    @Test
    fun `parses a full english line`() {
        val parsed = MedicationLineParser.parse("Panadol 500 mg tablets twice daily for 5 days")!!
        assertEquals("Panadol", parsed.name)
        assertEquals("500 mg", parsed.strength)
        assertEquals("tablet", parsed.form)
        assertEquals("twice daily", parsed.dose)
        assertEquals("for 5 days", parsed.duration)
    }

    @Test
    fun `parses a full arabic line`() {
        val parsed = MedicationLineParser.parse("أوجمنتين ١ جم أقراص قرص مرتين يومياً لمدة ٥ أيام")!!
        assertEquals("أوجمنتين", parsed.name)
        assertEquals("1 g", parsed.strength)
        assertEquals("tablet", parsed.form)
        assertEquals("مرتين", parsed.dose)
        assertEquals("لمدة 5 أيام", parsed.duration)
    }

    @Test
    fun `parses arabic strength units`() {
        val parsed = MedicationLineParser.parse("باراسيتامول ٥٠٠ مجم")!!
        assertEquals("باراسيتامول", parsed.name)
        assertEquals("500 mg", parsed.strength)
    }

    @Test
    fun `parses combined strength like mg per ml`() {
        assertEquals("250 mg/5 ml", MedicationLineParser.extractStrength("Amoxicillin 250mg/5ml"))
    }

    @Test
    fun `detects routes in both languages`() {
        assertEquals("oral", MedicationLineParser.extractRoute("Augmentin 1g oral"))
        assertEquals("oral", MedicationLineParser.extractRoute("أوجمنتين عن طريق الفم"))
        assertEquals("intravenous", MedicationLineParser.extractRoute("Injection IV"))
    }

    @Test
    fun `keeps digits attached to names like vitamin b12`() {
        val parsed = MedicationLineParser.parse("Vitamin B12 500 mcg")!!
        assertEquals("Vitamin B12", parsed.name)
        assertEquals("500 mcg", parsed.strength)
    }

    @Test
    fun `dose patterns are literal spans not inventions`() {
        val parsed = MedicationLineParser.parse("Augmentin 2 caps three times daily")!!
        assertEquals("2 caps", parsed.dose)
    }

    @Test
    fun `unclear names are flagged`() {
        assertTrue(MedicationLineParser.isUnclearName("Pana?ol"))
        assertTrue(MedicationLineParser.isUnclearName("اب؟"))
        assertTrue(MedicationLineParser.isUnclearName(""))
        assertTrue(!MedicationLineParser.isUnclearName("Panadol"))
    }

    @Test
    fun `never invents - lines without letters produce nothing`() {
        assertNull(MedicationLineParser.parse("500"))
        assertNull(MedicationLineParser.parse("???"))
        assertNull(MedicationLineParser.parse("   "))
    }

    @Test
    fun `never invents - a plain name has no fabricated fields`() {
        val parsed = MedicationLineParser.parse("Panadol")!!
        assertEquals("Panadol", parsed.name)
        assertNull(parsed.strength)
        assertNull(parsed.form)
        assertNull(parsed.dose)
        assertNull(parsed.route)
        assertNull(parsed.duration)
    }

    @Test
    fun `unknown words stay in the name rather than being invented away`() {
        val parsed = MedicationLineParser.parse("Zyxtrazine")!!
        assertEquals("Zyxtrazine", parsed.name)
        assertNotNull(parsed.rawText)
    }
}

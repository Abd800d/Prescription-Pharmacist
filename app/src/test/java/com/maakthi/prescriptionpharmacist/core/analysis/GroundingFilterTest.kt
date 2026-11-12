package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingFilterTest {

    @Test
    fun `grounded text passes`() {
        assertTrue(GroundingFilter.isGrounded("Panadol", "Panadol 500 mg tablets"))
        assertTrue(GroundingFilter.isGrounded("twice daily", "Panadol twice daily"))
    }

    @Test
    fun `invented fields fail`() {
        assertFalse(GroundingFilter.isGrounded("Metformin", "Panadol 500 mg tablets"))
        assertFalse(GroundingFilter.isGrounded("1000 mg dose", "Panadol 500 mg"))
    }

    @Test
    fun `ocr variants with close spelling pass`() {
        // OCR misread "Pana0ol" should still ground "Panadol".
        assertTrue(GroundingFilter.isGrounded("Panadol", "Pana0ol 500 mg"))
    }

    @Test
    fun `strength grounding checks numbers and unit aliases`() {
        assertTrue(GroundingFilter.isStrengthGrounded("500 mg", "Panadol 500 mg"))
        assertTrue(GroundingFilter.isStrengthGrounded("500 mg", "Panadol 500 مجم"))
        assertTrue(GroundingFilter.isStrengthGrounded("1 g", "أوجمنتين ١ جم"))
        assertFalse(GroundingFilter.isStrengthGrounded("1000 mg", "Panadol 500 mg"))
    }

    @Test
    fun `form and route aliases ground canonical values`() {
        assertTrue(GroundingFilter.isFormGrounded("tablet", "أقراص"))
        assertTrue(GroundingFilter.isFormGrounded("syrup", "شراب أطفال"))
        assertTrue(GroundingFilter.isRouteGrounded("oral", "عن طريق الفم"))
        assertFalse(GroundingFilter.isFormGrounded("injection", "أقراص"))
    }

    @Test
    fun `keep grounded drops partially grounded fields`() {
        val fields = GroundingFilter.keepGrounded(
            strength = "500 mg",
            form = "tablet",
            dose = "2 tablets daily", // "daily" is missing from the text
            route = "intravenous", // not in the text
            duration = null,
            ocrText = "Panadol 500 mg tablets 2 tablets"
        )
        assertEquals("500 mg", fields.strength)
        assertEquals("tablet", fields.form)
        assertNull(fields.dose)
        assertNull(fields.route)
        assertNull(fields.duration)
    }

    @Test
    fun `keep grounded keeps fully grounded fields`() {
        val fields = GroundingFilter.keepGrounded(
            strength = "500 mg",
            form = "tablet",
            dose = "2 tablets twice daily",
            route = null,
            duration = null,
            ocrText = "Panadol 500 mg tablets 2 tablets twice daily"
        )
        assertEquals("2 tablets twice daily", fields.dose)
    }
}

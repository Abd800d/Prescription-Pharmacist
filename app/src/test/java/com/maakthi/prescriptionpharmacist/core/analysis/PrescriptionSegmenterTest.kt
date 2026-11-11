package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionSegmenterTest {

    @Test
    fun `splits a line containing several medications`() {
        val lines = PrescriptionSegmenter.segment("Panadol 500mg, Augmentin 1g")
        assertEquals(2, lines.size)
        assertTrue(lines[0].text.contains("Panadol"))
        assertTrue(lines[1].text.contains("Augmentin"))
    }

    @Test
    fun `does not split dose patterns like one plus one`() {
        val lines = PrescriptionSegmenter.segment("Panadol 1+1+1")
        assertEquals(1, lines.size)
    }

    @Test
    fun `drops noise lines (dates, phones, headers, signatures)`() {
        val ocr = OcrResult(
            fullText = listOf(
                "Dr. Ahmed Clinic",
                "Date: 2024/01/01",
                "Tel: 0555123456",
                "Signature",
                "Panadol 500mg"
            ).joinToString("\n")
        )
        val lines = PrescriptionSegmenter.segment(ocr)
        assertEquals(1, lines.size)
        assertTrue(lines[0].text.contains("Panadol"))
    }

    @Test
    fun `drops pure number lines`() {
        val lines = PrescriptionSegmenter.segment("12345\n500\n- - -\nAugmentin 1g")
        assertEquals(1, lines.size)
    }

    @Test
    fun `keeps arabic medication lines`() {
        val lines = PrescriptionSegmenter.segment("أوجمنتين ١ جم أقراص\nالتاريخ")
        assertEquals(1, lines.size)
        assertTrue(lines[0].text.contains("أوجمنتين"))
    }

    @Test
    fun `preserves per-line ocr confidence`() {
        val ocr = OcrResult(
            fullText = "",
            lines = listOf(
                OcrLine("Panadol 500mg", 0.9f),
                OcrLine("Augmentin 1g", 0.4f)
            )
        )
        val lines = PrescriptionSegmenter.segment(ocr)
        assertEquals(0.9f, lines[0].ocrConfidence, 0.001f)
        assertEquals(0.4f, lines[1].ocrConfidence, 0.001f)
    }

    @Test
    fun `normalizes arabic-indic digits in the cleaned line`() {
        val lines = PrescriptionSegmenter.segment("أوجمنتين ٥٠٠ مجم")
        assertEquals("أوجمنتين 500 مجم", lines[0].text)
    }
}

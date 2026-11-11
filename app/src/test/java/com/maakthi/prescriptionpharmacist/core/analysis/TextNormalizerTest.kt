package com.maakthi.prescriptionpharmacist.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `arabic-indic digits become ascii`() {
        assertEquals("500", TextNormalizer.normalizeDigits("٥٠٠"))
        assertEquals("500", TextNormalizer.normalizeDigits("۵۰۰"))
        assertEquals("1g", TextNormalizer.normalizeDigits("١g"))
    }

    @Test
    fun `diacritics are stripped and letters unified`() {
        assertEquals(
            TextNormalizer.normalize("أوجمنتين"),
            TextNormalizer.normalize("أَوجمنتين")
        )
        assertEquals(
            TextNormalizer.normalize("اوجمنتين"),
            TextNormalizer.normalize("أوجمنتين")
        )
        assertEquals(TextNormalizer.normalize("سعالي"), TextNormalizer.normalize("سُعَالى"))
    }

    @Test
    fun `punctuation and case are normalized`() {
        assertEquals("panadol 500mg", TextNormalizer.normalize("Panadol, 500mg!"))
    }

    @Test
    fun `levenshtein distance is correct`() {
        assertEquals(3, TextNormalizer.levenshtein("kitten", "sitting"))
        assertEquals(0, TextNormalizer.levenshtein("same", "same"))
        assertEquals(4, TextNormalizer.levenshtein("", "abcd"))
    }

    @Test
    fun `similarity ranks identical and related above unrelated`() {
        assertEquals(1f, TextNormalizer.similarity("Panadol", "panadol"), 0.001f)
        val related = TextNormalizer.similarity("Panadol", "Panadeine")
        val unrelated = TextNormalizer.similarity("Panadol", "Zzzqq")
        assertTrue("related=$related unrelated=$unrelated", related > unrelated)
        assertTrue(related > 0.4f)
        assertTrue(unrelated < 0.35f)
    }

    @Test
    fun `token order does not matter much for similarity`() {
        val direct = TextNormalizer.similarity("Extra Panadol", "Panadol Extra")
        assertTrue("got $direct", direct >= 0.8f)
    }
}

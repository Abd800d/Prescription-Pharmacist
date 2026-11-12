package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugCatalogMatcherTest {

    private val catalog = listOf(
        Drug(id = "1", name = "Panadol", scientificName = "Paracetamol", type = MedicationType.BRAND),
        Drug(id = "2", name = "Panadol Extra", scientificName = "Paracetamol/Caffeine", type = MedicationType.BRAND),
        Drug(id = "3", name = "Panadeine", scientificName = "Paracetamol/Codeine", type = MedicationType.BRAND),
        Drug(id = "4", name = "Augmentin", scientificName = "Amoxicillin/Clavulanate", type = MedicationType.BRAND),
        Drug(id = "5", name = "أوجمنتين", scientificName = "أموكسيسيلين/كلافولانات", type = MedicationType.BRAND),
        Drug(id = "6", name = "Amoxicillin", type = MedicationType.GENERIC),
        Drug(id = "7", name = "Metformin", type = MedicationType.GENERIC)
    )

    @Test
    fun `exact match wins with full score`() {
        val result = DrugCatalogMatcher.match("Panadol", catalog)
        assertEquals("Panadol", result.matched?.name)
        assertEquals(1f, result.score, 0.001f)
    }

    @Test
    fun `similar names are ranked with the correct primary match`() {
        // "Panadol Extra" must not collapse to plain "Panadol".
        val result = DrugCatalogMatcher.match("Panadol Extra", catalog)
        assertEquals("Panadol Extra", result.matched?.name)
        assertTrue(result.alternatives.any { it.name == "Panadol" })
    }

    @Test
    fun `sibling brand names appear as alternatives`() {
        val result = DrugCatalogMatcher.match("Panadeine", catalog)
        assertEquals("Panadeine", result.matched?.name)
        assertTrue(
            "alternatives=${result.alternatives}",
            result.alternatives.any { it.name.startsWith("Panadol") }
        )
    }

    @Test
    fun `arabic names match despite letter unification differences`() {
        val result = DrugCatalogMatcher.match("اوجمنتين", catalog)
        assertEquals("أوجمنتين", result.matched?.name)
    }

    @Test
    fun `brand resolves its generic scientific name`() {
        val result = DrugCatalogMatcher.match("Augmentin", catalog)
        assertEquals("Amoxicillin/Clavulanate", result.matched?.scientificName)
        assertEquals(MedicationType.BRAND, result.matched?.type)
    }

    @Test
    fun `generic query matches generic record`() {
        val result = DrugCatalogMatcher.match("Amoxicillin", catalog)
        assertEquals("Amoxicillin", result.matched?.name)
        assertEquals(MedicationType.GENERIC, result.matched?.type)
    }

    @Test
    fun `never invents - unknown name yields no match`() {
        val result = DrugCatalogMatcher.match("Xyzzqtrazine", catalog)
        assertNull(result.matched)
    }

    @Test
    fun `never invents - empty query yields no match`() {
        assertNull(DrugCatalogMatcher.match("", catalog).matched)
        assertNull(DrugCatalogMatcher.match("Panadol", emptyList()).matched)
    }

    @Test
    fun `typo readings stay unmatched but keep suggestions`() {
        // A close-but-unmatched reading keeps suggestions without adopting a
        // wrong primary match.
        val result = DrugCatalogMatcher.match("Panadiene", catalog) // typo of Panadeine
        assertNull(result.matched)
        assertTrue(
            "alternatives=${result.alternatives}",
            result.alternatives.any { it.name == "Panadeine" }
        )
    }
}

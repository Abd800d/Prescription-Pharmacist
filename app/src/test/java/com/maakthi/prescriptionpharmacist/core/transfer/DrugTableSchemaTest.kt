package com.maakthi.prescriptionpharmacist.core.transfer

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drug table schema: bilingual headers (AR/EN), row validation, boolean
 * parsing, drug mapping, and the duplicate-drug key used to protect the
 * approved database from double entries.
 */
class DrugTableSchemaTest {

    // ---------- headers (Arabic + English) ----------

    @Test
    fun `arabic and english headers both map to canonical fields`() {
        assertEquals("tradeNameEnglish", DrugTableSchema.matchHeader("trade name english"))
        assertEquals("tradeNameEnglish", DrugTableSchema.matchHeader("Name"))
        assertEquals("tradeNameArabic", DrugTableSchema.matchHeader("الاسم التجاري العربي"))
        assertEquals("barcode", DrugTableSchema.matchHeader("الباركود"))
        assertEquals("manufacturer", DrugTableSchema.matchHeader("الشركة المصنعة"))
        assertNull(DrugTableSchema.matchHeader("random column"))
        assertNull(DrugTableSchema.matchHeader("Trade Name"))
    }

    @Test
    fun `mapColumns keeps only recognized columns`() {
        val mapped = DrugTableSchema.mapColumns(
            listOf("الاسم التجاري العربي", "Barcode", "unknown", "Strength")
        )
        assertEquals("tradeNameArabic", mapped[0])
        assertEquals("barcode", mapped[1])
        assertEquals("strength", mapped[3])
        assertFalse(mapped.containsKey(2))
    }

    // ---------- duplicate drugs ----------

    @Test
    fun `duplicate key joins name strength and manufacturer`() {
        val key = DrugTableSchema.duplicateKey(
            mapOf(
                "tradeNameEnglish" to "Panadol",
                "strength" to "500 mg",
                "manufacturer" to "GSK"
            )
        )
        assertNotNull(key)
        assertTrue(key!!.contains("panadol"))
    }

    @Test
    fun `same product in arabic and english does not collide`() {
        val en = DrugTableSchema.duplicateKey(mapOf("tradeNameEnglish" to "Panadol"))
        val ar = DrugTableSchema.duplicateKey(mapOf("tradeNameArabic" to "بنادول"))
        assertNotNull(en)
        assertNotNull(ar)
        assertFalse(en == ar)
    }

    @Test
    fun `a row without any name has no duplicate key`() {
        assertNull(DrugTableSchema.duplicateKey(mapOf("strength" to "500 mg")))
    }

    @Test
    fun `key is stable between file rows and database drugs`() {
        val rowKey = DrugTableSchema.duplicateKey(
            mapOf("tradeNameEnglish" to "Panadol", "strength" to "500 mg", "manufacturer" to "GSK")
        )
        val drug = Drug(
            id = "d1",
            name = "Panadol",
            tradeNameEnglish = "Panadol",
            strength = "500 mg",
            manufacturer = "GSK"
        )
        assertEquals(rowKey, DrugTableSchema.duplicateKeyOf(drug))
    }

    // ---------- validation & mapping ----------

    @Test
    fun `parseBoolean accepts arabic and english words`() {
        assertEquals(true, DrugTableSchema.parseBoolean("true"))
        assertEquals(true, DrugTableSchema.parseBoolean("نعم"))
        assertEquals(false, DrugTableSchema.parseBoolean("لا"))
        assertEquals(false, DrugTableSchema.parseBoolean("false"))
        assertNull(DrugTableSchema.parseBoolean("maybe"))
        assertNull(DrugTableSchema.parseBoolean(null))
    }

    @Test
    fun `toDrug maps values and keeps provenance fields`() {
        val drug = DrugTableSchema.toDrug(
            values = mapOf(
                "tradeNameEnglish" to "Panadol",
                "tradeNameArabic" to "بنادول",
                "strength" to "500 mg",
                "barcode" to "6281000000000",
                "sourceName" to "SFDA",
                "sourceUrl" to "https://sfda.example/drug/1",
                "sourceDate" to "2026-01-01"
            ),
            existingId = null,
            now = 1_000L
        )
        assertEquals("Panadol", drug.tradeNameEnglish)
        assertEquals("بنادول", drug.tradeNameArabic)
        assertEquals("SFDA", drug.sourceName)
        assertEquals(1_000L, drug.updatedAt)
        assertTrue(drug.id.isNotBlank())
    }

    @Test
    fun `toDrug with an existing id updates instead of creating`() {
        val drug = DrugTableSchema.toDrug(
            values = mapOf("tradeNameEnglish" to "Panadol"),
            existingId = "keep-me",
            now = 5L
        )
        assertEquals("keep-me", drug.id)
    }
}

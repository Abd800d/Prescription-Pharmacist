package com.maakthi.prescriptionpharmacist.core.transfer

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.RowProblemCode
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import planning: Arabic/English column names, missing-field detection,
 * duplicate detection, per-row errors, editing before save, and large files.
 */
class ImportPlannerTest {

    private fun drug(
        id: String,
        tradeEn: String,
        strength: String? = "500 mg",
        manufacturer: String? = "GSK",
        barcode: String? = null
    ) = Drug(
        id = id,
        name = tradeEn,
        tradeNameEnglish = tradeEn,
        strength = strength,
        manufacturer = manufacturer,
        barcode = barcode
    )

    // ---------- column mapping ----------

    @Test
    fun `arabic and english headers are both recognized`() {
        val headers = listOf(
            "الاسم التجاري (عربي)", "Trade Name English", "الاسم العلمي",
            "المادة الفعالة", "التركيز", "الشكل الصيدلاني", "الشركة المصنعة",
            "الباركود", "موثق", "unknown column"
        )
        val mapped = DrugTableSchema.mapColumns(headers)
        assertEquals("tradeNameArabic", mapped[0])
        assertEquals("tradeNameEnglish", mapped[1])
        assertEquals("genericNameEnglish", mapped[2])
        assertEquals("activeIngredient", mapped[3])
        assertEquals("strength", mapped[4])
        assertEquals("dosageForm", mapped[5])
        assertEquals("manufacturer", mapped[6])
        assertEquals("barcode", mapped[7])
        assertEquals("isVerified", mapped[8])
        assertNull(mapped[9])
    }

    // ---------- missing fields & errors ----------

    @Test
    fun `rows without any name get a blocking error`() {
        val preview = ImportPlanner.planTable(
            "f.csv", TransferFormat.CSV,
            TransferTable(
                listOf("التركيز", "الشركة المصنعة"),
                listOf(listOf("500 mg", "GSK"))
            ),
            existing = emptyList()
        )
        val row = preview.rows.single()
        assertTrue(row.hasBlockingErrors)
        assertTrue(row.errors.any { it.code == RowProblemCode.MISSING_NAME })
        assertFalse(row.included)
    }

    @Test
    fun `invalid barcode is reported on the row`() {
        val preview = ImportPlanner.planTable(
            "f.csv", TransferFormat.CSV,
            TransferTable(
                listOf("name", "barcode"),
                listOf(listOf("Panadol", "123-456!"))
            ),
            existing = emptyList()
        )
        val row = preview.rows.single()
        assertEquals(RowProblemCode.INVALID_BARCODE, row.errors.single().code)
        assertEquals("barcode", row.errors.single().field)
    }

    @Test
    fun `clean rows are included with no problems`() {
        val preview = ImportPlanner.planTable(
            "f.csv", TransferFormat.CSV,
            TransferTable(
                listOf("name", "strength"),
                listOf(listOf("Panadol", "500 mg"))
            ),
            existing = emptyList()
        )
        val row = preview.rows.single()
        assertTrue(row.errors.isEmpty())
        assertTrue(row.warnings.isEmpty())
        assertTrue(row.included)
    }

    // ---------- duplicates ----------

    @Test
    fun `duplicates inside the file are detected and excluded by default`() {
        val preview = ImportPlanner.planTable(
            "f.csv", TransferFormat.CSV,
            TransferTable(
                listOf("name", "strength", "manufacturer"),
                listOf(
                    listOf("Panadol", "500 mg", "GSK"),
                    listOf("pana dol", "500mg", "gsk"), // same normalized key
                    listOf("Augmentin", "1 g", "GSK")
                )
            ),
            existing = emptyList()
        )
        val first = preview.rows[0]
        val second = preview.rows[1]
        assertTrue(first.included)
        assertFalse(second.included)
        val problem = second.warnings.single()
        assertEquals(RowProblemCode.DUPLICATE_IN_FILE, problem.code)
        assertEquals("1", problem.detail) // earlier row number
    }

    @Test
    fun `duplicates against the database are detected and excluded by default`() {
        val existing = listOf(drug("db-1", "Panadol", barcode = "999"))
        val preview = ImportPlanner.planTable(
            "f.csv", TransferFormat.CSV,
            TransferTable(
                listOf("name", "strength", "manufacturer", "barcode"),
                listOf(
                    listOf("Panadol", "500 mg", "GSK", "123"), // same name key as db-1
                    listOf("Other", "1 g", "ACME", "999") // same barcode as db-1
                )
            ),
            existing = existing
        )
        preview.rows.forEach { row ->
            assertFalse(row.included)
            assertEquals(RowProblemCode.DUPLICATE_IN_DB, row.warnings.single().code)
            assertEquals("db-1", row.warnings.single().detail)
        }
    }

    // ---------- editing before save ----------

    @Test
    fun `editing a row fixes its problems and keeps the decision`() {
        val rows = ImportPlanner.planValues(
            listOf(
                1 to mapOf("tradeNameEnglish" to "Panadol", "barcode" to "123-456!"),
                2 to mapOf("tradeNameEnglish" to "Augmentin")
            ),
            existing = emptyList()
        )
        assertTrue(rows[0].hasBlockingErrors)

        // The user fixes the barcode in the preview editor.
        val fixed = ImportPlanner.replanRow(
            row = rows[0],
            newValues = rows[0].values + ("barcode" to "123456"),
            allRows = rows,
            existing = emptyList()
        )
        assertTrue(fixed.errors.isEmpty())
        assertTrue(fixed.included)

        // The user excludes row 2 explicitly, then edits it — decision kept.
        val excluded = rows[1].copy(included = false)
        val stillExcluded = ImportPlanner.replanRow(
            row = excluded,
            newValues = excluded.values + ("strength" to "875 mg"),
            allRows = rows,
            existing = emptyList()
        )
        assertFalse(stillExcluded.included)

        // A row that BECOMES invalid is never included.
        val broken = ImportPlanner.replanRow(
            row = fixed,
            newValues = fixed.values + ("tradeNameEnglish" to ""),
            allRows = rows,
            existing = emptyList()
        )
        assertFalse(broken.included)
    }

    // ---------- values → drug ----------

    @Test
    fun `approved rows become drugs with all fields and generated ids`() {
        val values = mapOf(
            "tradeNameArabic" to "بنادول",
            "tradeNameEnglish" to "Panadol",
            "genericNameEnglish" to "Paracetamol",
            "activeIngredient" to "Paracetamol",
            "strength" to "500 mg",
            "dosageForm" to "tablet",
            "manufacturer" to "GSK",
            "barcode" to "1234567890",
            "isVerified" to "نعم",
            "sourceName" to "openFDA"
        )
        val drug = DrugTableSchema.toDrug(values, existingId = null, now = 42L)
        assertTrue(drug.id.isNotEmpty())
        assertEquals("Panadol", drug.name)
        assertEquals("بنادول", drug.tradeNameArabic)
        assertEquals("Paracetamol", drug.genericNameEnglish)
        assertEquals("1234567890", drug.barcode)
        assertTrue(drug.isVerified)
        assertEquals(42L, drug.updatedAt)

        val updated = DrugTableSchema.toDrug(values, existingId = "db-1", now = 43L)
        assertEquals("db-1", updated.id)
    }

    @Test
    fun `boolean parsing accepts english and arabic`() {
        assertEquals(true, DrugTableSchema.parseBoolean("true"))
        assertEquals(true, DrugTableSchema.parseBoolean("1"))
        assertEquals(true, DrugTableSchema.parseBoolean("نعم"))
        assertEquals(false, DrugTableSchema.parseBoolean("لا"))
        assertEquals(false, DrugTableSchema.parseBoolean(""))
        assertNull(DrugTableSchema.parseBoolean("maybe"))
    }

    // ---------- large files ----------

    @Test
    fun `large file with five thousand rows plans quickly`() {
        val headers = listOf("name", "strength", "manufacturer")
        val rows = (1..5_000).map { listOf("Drug $it", "$it mg", "Maker ${it % 10}") }
        val preview = ImportPlanner.planTable(
            "big.csv", TransferFormat.CSV, TransferTable(headers, rows), existing = emptyList()
        )
        assertEquals(5_000, preview.rows.size)
        assertEquals(5_000, preview.includedRows.size)
        assertEquals(0, preview.rowsWithErrors)
    }

    @Test
    fun `export values cover every exported field`() {
        val values = ImportPlanner.drugToValues(drug("d1", "Panadol"))
        assertEquals(DrugTableSchema.EXPORT_FIELDS.toSet(), values.keys)
    }

    @Test
    fun `full csv roundtrip through the planner keeps records consistent`() {
        // Export 3 drugs → CSV → plan as import against an empty DB.
        val drugs = listOf(
            drug("d1", "Panadol", barcode = "1"),
            drug("d2", "Augmentin", strength = "1 g", barcode = "2"),
            drug("d3", "Zyrtec", strength = "10 mg", manufacturer = "UCB", barcode = "3")
        )
        val csv = CsvCodec.write(ImportPlanner.drugTable(drugs))
        val preview = ImportPlanner.planTable(
            "export.csv", TransferFormat.CSV, CsvCodec.parse(csv), existing = emptyList()
        )
        assertEquals(3, preview.rows.size)
        assertTrue(preview.rows.all { it.included })
        val rebuilt = DrugTableSchema.toDrug(preview.rows[1].values, existingId = null, now = 1L)
        assertEquals("Augmentin", rebuilt.tradeNameEnglish)
        assertEquals("1 g", rebuilt.strength)
    }
}

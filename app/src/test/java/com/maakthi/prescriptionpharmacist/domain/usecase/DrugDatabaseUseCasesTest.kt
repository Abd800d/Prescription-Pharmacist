package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.VerificationFilter
import com.maakthi.prescriptionpharmacist.domain.model.drugChanges
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drug database behaviour: add/edit/delete, duplicate prevention, the
 * change history, multi-field search and result filters.
 */
class DrugDatabaseUseCasesTest {

    private class FakeDrugEntryRepository : DrugEntryRepository {
        val store = LinkedHashMap<String, Drug>()
        val audit = mutableListOf<DrugAuditEntry>()

        override fun observeDrugs(): Flow<List<Drug>> = flowOf(store.values.toList())
        override suspend fun getDrug(id: String): Drug? = store[id]
        override suspend fun searchLocal(query: String): List<Drug> =
            searchDrugs(query, DrugFilters())

        override suspend fun upsertDrug(drug: Drug) {
            store[drug.id] = drug
        }

        override suspend fun deleteDrug(id: String) {
            store.remove(id)
        }

        override suspend fun searchRemote(query: String): List<Drug> = emptyList()

        override suspend fun searchDrugs(query: String, filters: DrugFilters): List<Drug> =
            store.values.filter { drug ->
                val matchesQuery = query.isEmpty() || listOf(
                    drug.tradeNameArabic, drug.tradeNameEnglish,
                    drug.genericNameArabic, drug.genericNameEnglish,
                    drug.activeIngredient, drug.barcode, drug.name
                ).any { it?.contains(query, ignoreCase = true) == true }
                val matchesForm = filters.dosageForm == null || drug.dosageForm == filters.dosageForm
                val matchesManufacturer =
                    filters.manufacturer == null || drug.manufacturer == filters.manufacturer
                val matchesVerification = when (filters.verification) {
                    VerificationFilter.ALL -> true
                    VerificationFilter.VERIFIED -> drug.isVerified
                    VerificationFilter.MANUAL -> !drug.isVerified
                }
                matchesQuery && matchesForm && matchesManufacturer && matchesVerification
            }

        override suspend fun getDrugByBarcode(barcode: String): Drug? =
            store.values.firstOrNull { it.barcode == barcode.trim() }

        override suspend fun findDuplicate(
            name: String,
            strength: String?,
            manufacturer: String?,
            excludeId: String?
        ): Drug? {
            val key = DrugEntryValidator.duplicateKey(name, strength, manufacturer)
            return store.values.firstOrNull {
                it.id != excludeId &&
                    DrugEntryValidator.duplicateKey(
                        DrugEntryValidator.primaryName(it), it.strength, it.manufacturer
                    ) == key
            }
        }

        override suspend fun recordAudit(entry: DrugAuditEntry) {
            audit.add(entry)
        }

        override fun observeAudit(drugId: String): Flow<List<DrugAuditEntry>> =
            flowOf(audit.filter { it.drugId == drugId }.asReversed())

        override suspend fun getAudit(drugId: String): List<DrugAuditEntry> =
            audit.filter { it.drugId == drugId }.asReversed()

        override suspend fun filterOptions(): DrugFilterOptions = DrugFilterOptions(
            dosageForms = store.values.mapNotNull { it.dosageForm }.distinct(),
            manufacturers = store.values.mapNotNull { it.manufacturer }.distinct()
        )

        override suspend fun searchApi(query: String): List<Drug> = emptyList()
    }

    private val repo = FakeDrugEntryRepository()
    private val add = AddDrugUseCase(repo)
    private val update = UpdateDrugUseCase(repo)
    private val delete = DeleteDrugUseCase(repo)
    private val search = SearchDrugsUseCase(repo)
    private val getAudit = GetDrugAuditUseCase(repo)

    private fun drug(
        id: String = "d1",
        tradeAr: String? = "بنادول",
        tradeEn: String? = "Panadol",
        genericAr: String? = null,
        genericEn: String? = "Paracetamol",
        ingredient: String? = "Paracetamol",
        strength: String? = "500 mg",
        form: String? = "tablet",
        manufacturer: String? = "GSK",
        barcode: String? = "1234567890",
        verified: Boolean = false
    ) = Drug(
        id = id,
        name = tradeEn ?: tradeAr ?: genericEn ?: genericAr ?: "",
        scientificName = genericEn,
        tradeNameArabic = tradeAr,
        tradeNameEnglish = tradeEn,
        genericNameArabic = genericAr,
        genericNameEnglish = genericEn,
        activeIngredient = ingredient,
        strength = strength,
        dosageForm = form,
        manufacturer = manufacturer,
        barcode = barcode,
        isVerified = verified
    )

    // ---------- add ----------

    @Test
    fun `add persists the drug and writes an ADD audit entry`() = runTest {
        val result = add(drug(), performedBy = "sara")
        assertTrue(result.isSuccess)
        val saved = result.getOrNull()!!
        assertEquals("sara", saved.createdBy)
        assertTrue(saved.updatedAt > 0)
        assertEquals(1, repo.store.size)
        assertEquals(1, getAudit(saved.id).size)
        assertEquals(DrugAuditAction.ADD, getAudit(saved.id).first().action)
        assertEquals("sara", getAudit(saved.id).first().performedBy)
    }

    @Test
    fun `add rejects a record without any name`() = runTest {
        val result = add(drug(tradeAr = null, tradeEn = null, genericEn = null), performedBy = "sara")
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION, (result as AppResult.Error).code)
        assertTrue(repo.store.isEmpty())
    }

    @Test
    fun `add rejects an invalid barcode`() = runTest {
        val result = add(drug(barcode = "123-456!"), performedBy = "sara")
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION, (result as AppResult.Error).code)
    }

    // ---------- duplicate prevention ----------

    @Test
    fun `add rejects a duplicate barcode`() = runTest {
        add(drug(id = "d1", barcode = "999"), performedBy = "sara")
        val result = add(
            drug(id = "d2", tradeEn = "Completely Different", barcode = "999"),
            performedBy = "sara"
        )
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.DUPLICATE, (result as AppResult.Error).code)
        assertEquals(1, repo.store.size)
    }

    @Test
    fun `add rejects the same name strength and manufacturer ignoring case and spaces`() = runTest {
        add(drug(id = "d1"), performedBy = "sara")
        val result = add(
            drug(id = "d2", tradeEn = "pana dol", strength = "500mg", manufacturer = "gsk", barcode = "888"),
            performedBy = "omar"
        )
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.DUPLICATE, (result as AppResult.Error).code)
    }

    @Test
    fun `add allows the same name with a different strength`() = runTest {
        add(drug(id = "d1", strength = "500 mg"), performedBy = "sara")
        val result = add(drug(id = "d2", strength = "1 g", barcode = "888"), performedBy = "sara")
        assertTrue(result.isSuccess)
        assertEquals(2, repo.store.size)
    }

    @Test
    fun `update does not collide with itself`() = runTest {
        add(drug(id = "d1"), performedBy = "sara")
        val result = update(
            drug(id = "d1", tradeAr = "بنادول", tradeEn = "Panadol", strength = "500 mg"),
            performedBy = "sara"
        )
        assertTrue(result.isSuccess)
    }

    // ---------- update / change history ----------

    @Test
    fun `update records the changed fields in the audit log`() = runTest {
        add(drug(id = "d1", strength = "500 mg"), performedBy = "sara")
        val result = update(drug(id = "d1", strength = "1 g", manufacturer = "SPIMACO"), performedBy = "omar")
        assertTrue(result.isSuccess)
        val entries = getAudit("d1")
        assertEquals(2, entries.size)
        val updateEntry = entries.first { it.action == DrugAuditAction.UPDATE }
        assertEquals("omar", updateEntry.performedBy)
        assertTrue(updateEntry.changes.contains("strength: 500 mg → 1 g"))
        assertTrue(updateEntry.changes.contains("manufacturer: GSK → SPIMACO"))
    }

    @Test
    fun `update rejects a missing record`() = runTest {
        val result = update(drug(id = "ghost"), performedBy = "sara")
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.NOT_FOUND, (result as AppResult.Error).code)
    }

    // ---------- delete ----------

    @Test
    fun `delete removes the record and keeps its history`() = runTest {
        add(drug(id = "d1"), performedBy = "sara")
        val result = delete("d1", performedBy = "omar")
        assertTrue(result.isSuccess)
        assertNull(repo.store["d1"])
        val entries = getAudit("d1")
        assertEquals(2, entries.size)
        assertEquals(DrugAuditAction.DELETE, entries.first().action)
        assertEquals("omar", entries.first().performedBy)
    }

    // ---------- search ----------

    @Test
    fun `search finds by arabic trade name`() = runTest {
        add(drug(id = "d1"), performedBy = "sara")
        assertEquals(1, search("بنادول").size)
    }

    @Test
    fun `search finds by generic name, ingredient and barcode`() = runTest {
        add(drug(id = "d1"), performedBy = "sara")
        assertEquals(1, search("Paracetamol").size)
        assertEquals(1, search("1234567890").size)
        assertTrue(search("nonexistent").isEmpty())
    }

    // ---------- filters ----------

    @Test
    fun `filters narrow the results`() = runTest {
        add(drug(id = "d1", form = "tablet", manufacturer = "GSK"), performedBy = "sara")
        add(
            drug(
                id = "d2", tradeEn = "Augmentin", tradeAr = "أوجمنتين",
                strength = "1 g", barcode = "222", form = "suspension", manufacturer = "GSK"
            ),
            performedBy = "sara"
        )
        assertEquals(2, search("", DrugFilters()).size)
        assertEquals(1, search("", DrugFilters(dosageForm = "tablet")).size)
        assertEquals(1, search("", DrugFilters(dosageForm = "suspension")).size)
        assertEquals(2, search("", DrugFilters(manufacturer = "GSK")).size)
        assertEquals(0, search("", DrugFilters(manufacturer = "Pfizer")).size)
    }

    // ---------- verified vs manual ----------

    @Test
    fun `verified records are distinguishable from manual ones`() = runTest {
        add(drug(id = "d1", verified = false), performedBy = "sara")
        add(
            drug(id = "d2", tradeEn = "Augmentin", tradeAr = "أوجمنتين", strength = "1 g", barcode = "222", verified = true),
            performedBy = "import"
        )
        assertEquals(1, search("", DrugFilters(verification = VerificationFilter.VERIFIED)).size)
        assertEquals(1, search("", DrugFilters(verification = VerificationFilter.MANUAL)).size)
        assertFalse(repo.store.getValue("d1").isVerified)
        assertTrue(repo.store.getValue("d2").isVerified)
    }

    // ---------- audit diff ----------

    @Test
    fun `drugChanges lists only the fields that changed`() {
        val old = drug(strength = "500 mg", manufacturer = "GSK")
        val new = drug(strength = "1 g", manufacturer = "GSK")
        val changes = drugChanges(old, new)
        assertTrue(changes.contains("strength: 500 mg → 1 g"))
        assertFalse(changes.contains("manufacturer"))
        assertEquals("", drugChanges(old, old))
    }

    @Test
    fun `primaryName prefers the english trade name`() {
        assertEquals("Panadol", DrugEntryValidator.primaryName(drug()))
        assertEquals(
            "بنادول",
            DrugEntryValidator.primaryName(drug(tradeEn = null, genericEn = null))
        )
        assertNotNull(DrugEntryValidator.primaryGeneric(drug()))
    }
}

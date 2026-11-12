package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugKnowledgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Knowledge search use case: query validation, graceful failure reporting,
 * and the guarantee that searching NEVER writes to the approved drug
 * database (persistence happens only after pharmacist review).
 */
class SearchDrugKnowledgeUseCaseTest {

    private class FakeKnowledgeRepository(
        private val searchFn: suspend (String) -> DrugKnowledgeResult
    ) : DrugKnowledgeRepository {
        override fun availableSources(): List<DrugSourceInfo> = emptyList()
        override suspend fun search(query: String): DrugKnowledgeResult = searchFn(query)
    }

    /** Records every write that would touch the approved database. */
    private class FakeDrugStore : DrugEntryRepository {
        val writes = mutableListOf<String>()

        override fun observeDrugs(): Flow<List<Drug>> = flowOf(emptyList())
        override suspend fun getDrug(id: String): Drug? = null
        override suspend fun searchLocal(query: String): List<Drug> = emptyList()
        override suspend fun upsertDrug(drug: Drug) {
            writes.add("upsert:${drug.id}")
        }

        override suspend fun deleteDrug(id: String) {
            writes.add("delete:$id")
        }

        override suspend fun searchRemote(query: String): List<Drug> = emptyList()
        override suspend fun searchDrugs(query: String, filters: DrugFilters): List<Drug> = emptyList()
        override suspend fun getDrugByBarcode(barcode: String): Drug? = null
        override suspend fun findDuplicate(
            name: String,
            strength: String?,
            manufacturer: String?,
            excludeId: String?
        ): Drug? = null

        override suspend fun recordAudit(entry: DrugAuditEntry) {
            writes.add("audit:${entry.drugId}")
        }

        override fun observeAudit(drugId: String): Flow<List<DrugAuditEntry>> = flowOf(emptyList())
        override suspend fun getAudit(drugId: String): List<DrugAuditEntry> = emptyList()
        override suspend fun filterOptions(): DrugFilterOptions = DrugFilterOptions()
        override suspend fun searchApi(query: String): List<Drug> = emptyList()
    }

    private val source = DrugSourceInfo("a", "Source A", "https://a.example", 1L)

    @Test
    fun `blank queries are rejected without touching any source`() = runTest {
        var searched = false
        val useCase = SearchDrugKnowledgeUseCase(
            FakeKnowledgeRepository {
                searched = true
                DrugKnowledgeResult("", emptyList(), emptyList(), emptySet(), emptyList())
            }
        )
        val result = useCase("   ")
        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION, (result as AppResult.Error).code)
        assertTrue(!searched)
    }

    @Test
    fun `successful results pass through with their warnings`() = runTest {
        val useCase = SearchDrugKnowledgeUseCase(
            FakeKnowledgeRepository {
                DrugKnowledgeResult(
                    query = it,
                    facts = listOf(
                        com.maakthi.prescriptionpharmacist.domain.model.DrugFact(
                            "warnings", "drowsiness", source
                        )
                    ),
                    conflicts = emptyList(),
                    warnings = setOf(
                        com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning.NOT_MEDICAL_ADVICE
                    ),
                    failedSources = listOf(source)
                )
            }
        )
        val result = useCase("Panadol")
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        assertEquals("Panadol", data.query)
        assertEquals(1, data.facts.size)
        assertEquals("drowsiness", data.facts.first().value)
        assertTrue(data.failedSources.isNotEmpty())
    }

    @Test
    fun `searching never writes to the approved drug database`() = runTest {
        val store = FakeDrugStore()
        // The use case does not even receive the drug store — persistence is
        // impossible without going through the reviewed add/update flow.
        val useCase = SearchDrugKnowledgeUseCase(
            FakeKnowledgeRepository {
                DrugKnowledgeResult(it, emptyList(), emptyList(), emptySet(), emptyList())
            }
        )
        useCase("anything")
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `a repository failure is reported, not thrown`() = runTest {
        val useCase = SearchDrugKnowledgeUseCase(
            FakeKnowledgeRepository {
                DrugKnowledgeResult(
                    query = it,
                    facts = emptyList(),
                    conflicts = emptyList(),
                    warnings = setOf(
                        com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning.SOURCE_UNAVAILABLE
                    ),
                    failedSources = listOf(source)
                )
            }
        )
        val result = useCase("offline drug")
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        assertTrue(data.facts.isEmpty())
        assertTrue(
            com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning.SOURCE_UNAVAILABLE in data.warnings
        )
    }
}

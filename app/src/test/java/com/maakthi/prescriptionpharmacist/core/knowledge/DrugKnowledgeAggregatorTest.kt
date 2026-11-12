package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Knowledge aggregation: duplicate facts are de-duplicated, disagreeing
 * sources become conflicts with NO automatic winner, and failed sources are
 * reported as warnings.
 */
class DrugKnowledgeAggregatorTest {

    private fun source(id: String = "s1", name: String = "Source $id") =
        DrugSourceInfo(id = id, name = name, url = "https://$id.example", retrievedAt = 100L, isOfficial = true)

    private fun fact(field: String, value: String, src: DrugSourceInfo = source()) =
        DrugFact(field = field, value = value, source = src)

    @Test
    fun `identical values from several sources are de-duplicated`() {
        val s1 = source("s1")
        val s2 = source("s2")
        val result = DrugKnowledgeAggregator.merge(
            query = "Panadol",
            outcomes = listOf(
                ProviderOutcome(s1, AppResult.success(listOf(fact("indications", "pain relief", s1)))),
                ProviderOutcome(s2, AppResult.success(listOf(fact("indications", "pain relief", s2))))
            )
        )
        assertEquals(1, result.facts.size)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `value variants that mean the same thing de-duplicate too`() {
        val result = DrugKnowledgeAggregator.merge(
            query = "panadol",
            outcomes = listOf(
                ProviderOutcome(source("s1"), AppResult.success(listOf(fact("strength", "500 mg")))),
                ProviderOutcome(source("s2"), AppResult.success(listOf(fact("strength", "500-mg"))))
            )
        )
        assertEquals(1, result.facts.size)
    }

    @Test
    fun `disagreeing sources become a conflict — never a silent winner`() {
        val s1 = source("official-a", "Ministry A")
        val s2 = source("official-b", "Ministry B")
        val result = DrugKnowledgeAggregator.merge(
            query = "Panadol",
            outcomes = listOf(
                ProviderOutcome(s1, AppResult.success(listOf(fact("pregnancy", "safe", s1)))),
                ProviderOutcome(s2, AppResult.success(listOf(fact("pregnancy", "avoid", s2))))
            )
        )
        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts.single()
        assertEquals("pregnancy", conflict.field)
        // BOTH values are preserved with their sources — the user decides
        assertEquals(2, conflict.facts.size)
        assertTrue(conflict.facts.any { it.value == "safe" && it.source.name == "Ministry A" })
        assertTrue(conflict.facts.any { it.value == "avoid" && it.source.name == "Ministry B" })
        assertTrue(KnowledgeWarning.CONFLICT_PRESENT in result.warnings)
    }

    @Test
    fun `failed sources become warnings and the rest still arrives`() {
        val ok = source("ok")
        val result = DrugKnowledgeAggregator.merge(
            query = "Panadol",
            outcomes = listOf(
                ProviderOutcome(ok, AppResult.success(listOf(fact("indications", "pain", ok)))),
                ProviderOutcome(source("down"), AppResult.error(ErrorCode.NETWORK, "offline"))
            )
        )
        assertEquals(1, result.facts.size)
        assertEquals(listOf("down"), result.failedSources.map { it.id })
        assertTrue(KnowledgeWarning.SOURCE_UNAVAILABLE in result.warnings)
        assertTrue(KnowledgeWarning.PARTIAL_RESULTS in result.warnings)
    }

    @Test
    fun `every result carries the not-medical-advice warning`() {
        val result = DrugKnowledgeAggregator.merge(
            query = "x",
            outcomes = listOf(ProviderOutcome(source("s"), AppResult.success(listOf(fact("indications", "pain")))))
        )
        assertTrue(KnowledgeWarning.NOT_MEDICAL_ADVICE in result.warnings)
    }

    @Test
    fun `arabic values are preserved and attributed`() {
        val src = source("sfda", "الهيئة السعودية")
        val result = DrugKnowledgeAggregator.merge(
            query = "بنادول",
            outcomes = listOf(
                ProviderOutcome(src, AppResult.success(listOf(fact("indications", "مسكن للألم", src))))
            )
        )
        assertEquals("مسكن للألم", result.facts.single().value)
        assertEquals("الهيئة السعودية", result.facts.single().source.name)
    }
}

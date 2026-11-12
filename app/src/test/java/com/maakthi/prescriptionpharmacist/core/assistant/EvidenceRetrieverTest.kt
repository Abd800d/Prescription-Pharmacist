package com.maakthi.prescriptionpharmacist.core.assistant

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeConflict
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RAG retrieval: local DB + official sources, bilingual topics, sufficiency. */
class EvidenceRetrieverTest {

    private fun drug(
        id: String,
        tradeEn: String? = null,
        tradeAr: String? = null,
        indications: String? = null,
        warnings: String? = null,
        interactions: String? = null,
        strength: String? = null,
        sourceName: String? = null,
        sourceUrl: String? = null,
        sourceDate: String? = null
    ) = Drug(
        id = id,
        name = tradeEn ?: tradeAr ?: id,
        tradeNameEnglish = tradeEn,
        tradeNameArabic = tradeAr,
        indications = indications,
        warnings = warnings,
        interactions = interactions,
        strength = strength,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        sourceDate = sourceDate
    )

    @Test
    fun `arabic question matches arabic drug name and builds attributed evidence`() {
        val drugs = listOf(
            drug(
                "d1", tradeAr = "بنادول", indications = "مسكن وخافض للحرارة",
                sourceName = "openFDA", sourceUrl = "https://fda.example/x", sourceDate = "2026-01-01"
            )
        )
        val pack = EvidenceRetriever.retrieve("ما هي استعمالات البنادول؟", drugs, knowledge = null)
        assertTrue(pack.items.isNotEmpty())
        assertEquals(listOf("بنادول"), pack.matchedDrugs)
        val item = pack.items.first()
        assertEquals("openFDA", item.sourceName)
        assertEquals("https://fda.example/x", item.sourceUrl)
        assertEquals("2026-01-01", item.sourceDate)
        assertTrue(item.text.contains("بنادول"))
    }

    @Test
    fun `english question matches english name and default topics`() {
        val drugs = listOf(
            drug("d1", tradeEn = "Panadol", indications = "pain", warnings = "liver", interactions = "warfarin")
        )
        val pack = EvidenceRetriever.retrieve("Tell me about Panadol", drugs, knowledge = null)
        // No topic keyword → default fields → all three values present.
        assertEquals(3, pack.items.size)
    }

    @Test
    fun `topic keywords filter the fields`() {
        val drugs = listOf(
            drug("d1", tradeEn = "Panadol", indications = "pain", warnings = "liver", interactions = "warfarin")
        )
        val pack = EvidenceRetriever.retrieve("Panadol interactions with warfarin?", drugs, knowledge = null)
        assertTrue(pack.items.isNotEmpty())
        assertTrue(pack.items.all { it.text.contains("Interactions") || it.text.contains("warfarin") })
        assertFalse(pack.items.any { it.text.contains("pain") })
    }

    @Test
    fun `arabic topic keywords map to fields`() {
        val drugs = listOf(
            drug("d1", tradeAr = "أوجمنتين", warnings = "حساسية البنسلين", indications = "التهابات")
        )
        val pack = EvidenceRetriever.retrieve("ما هي تحذيرات الأوجمنتين؟", drugs, knowledge = null)
        assertTrue(pack.items.isNotEmpty())
        assertTrue(pack.items.any { it.text.contains("التحذيرات") })
    }

    @Test
    fun `local records without source fall back to the local database label`() {
        val drugs = listOf(drug("d1", tradeEn = "Panadol", indications = "pain"))
        val pack = EvidenceRetriever.retrieve("what is Panadol used for", drugs, knowledge = null, language = "en")
        assertEquals("Local drug database", pack.items.first().sourceName)
    }

    @Test
    fun `knowledge facts are included with their source`() {
        val source = DrugSourceInfo("rxn", "RxNorm", "https://rxnav.example", 1_700_000_000_000L, true)
        val knowledge = DrugKnowledgeResult(
            query = "Panadol",
            facts = listOf(DrugFact("indications", "analgesic", source)),
            conflicts = emptyList(),
            warnings = emptySet(),
            failedSources = emptyList()
        )
        val pack = EvidenceRetriever.retrieve("Panadol", emptyList(), knowledge)
        assertTrue(pack.usedKnowledge)
        assertEquals(1, pack.items.size)
        assertEquals("RxNorm", pack.items.first().sourceName)
        assertTrue(pack.items.first().sourceDate.isNotBlank())
    }

    @Test
    fun `conflicts are surfaced, never silently resolved`() {
        val a = DrugSourceInfo("a", "DailyMed", "https://dailymed.example", 1L, true)
        val b = DrugSourceInfo("b", "openFDA", "https://fda.example", 2L, true)
        val knowledge = DrugKnowledgeResult(
            query = "X",
            facts = emptyList(),
            conflicts = listOf(
                DrugKnowledgeConflict(
                    field = "warnings",
                    facts = listOf(DrugFact("warnings", "A says avoid", a), DrugFact("warnings", "B says ok", b))
                )
            ),
            warnings = emptySet(),
            failedSources = emptyList()
        )
        val pack = EvidenceRetriever.retrieve("X warnings", emptyList(), knowledge)
        assertTrue(pack.conflictsNoted)
        assertTrue(pack.items.any { it.text.contains("A says avoid") && it.text.contains("B says ok") })
    }

    @Test
    fun `nothing relevant means insufficient`() {
        val drugs = listOf(drug("d1", tradeEn = "Panadol", indications = "pain"))
        val pack = EvidenceRetriever.retrieve("من هو رئيس فرنسا؟", drugs, knowledge = null)
        assertTrue(pack.insufficient)
    }

    @Test
    fun `max three drugs and bounded items`() {
        val drugs = (1..10).map {
            drug("d$it", tradeEn = "Panadol$it", indications = "i$it", warnings = "w$it",
                interactions = "x$it", strength = "s$it")
        }
        val pack = EvidenceRetriever.retrieve("Panadol1 Panadol2 Panadol3 Panadol4 Panadol5", drugs, knowledge = null)
        assertTrue(pack.matchedDrugs.size <= 3)
        assertTrue(pack.items.size <= 30)
    }

    @Test
    fun `knowledge query prefers the matched drug name`() {
        val drugs = listOf(drug("d1", tradeEn = "Panadol"))
        assertEquals("Panadol", EvidenceRetriever.knowledgeQuery("what about Panadol?", drugs))
    }
}

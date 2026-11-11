package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugSourceInfo
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Official-sources search behaviour: attribution, conflict handling with no
 * automatic selection, offline/timeout degradation, and retries.
 */
class DrugKnowledgeServiceTest {

    private class FakeProvider(
        override val id: String,
        private val respond: suspend (String) -> AppResult<List<DrugFact>>
    ) : DrugKnowledgeProvider {
        var calls = 0

        override val displayName: String = "Source $id"
        override val sourceUrl: String = "https://$id.example"
        override val isOfficial: Boolean = true

        override suspend fun fetchFacts(query: String): AppResult<List<DrugFact>> {
            calls++
            return respond(query)
        }
    }

    private fun source(id: String) = DrugSourceInfo(
        id = id, name = "Source $id", url = "https://$id.example",
        retrievedAt = 1_000L, isOfficial = true
    )

    private fun fact(field: String, value: String, id: String) =
        DrugFact(field, value, source(id))

    // ---------- attribution & merge ----------

    @Test
    fun `facts keep their source attribution`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { AppResult.success(listOf(fact("indications", "pain relief", "a"))) },
                FakeProvider("b") { AppResult.success(listOf(fact("warnings", "do not exceed dose", "b"))) }
            )
        )
        val result = service.search("Panadol")
        assertEquals(2, result.facts.size)
        assertEquals("a", result.facts.first { it.field == "indications" }.source.id)
        assertEquals("b", result.facts.first { it.field == "warnings" }.source.id)
        assertEquals("https://a.example", result.facts.first { it.source.id == "a" }.source.url)
    }

    @Test
    fun `identical values from several sources do not conflict`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { AppResult.success(listOf(fact("indications", "pain relief", "a"))) },
                FakeProvider("b") { AppResult.success(listOf(fact("indications", "Pain  relief", "b"))) }
            )
        )
        val result = service.search("Panadol")
        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, result.facts.size)
    }

    // ---------- conflicts: never auto-picked ----------

    @Test
    fun `conflicting sources are reported and no winner is chosen`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { AppResult.success(listOf(fact("warnings", "avoid in liver disease", "a"))) },
                FakeProvider("b") { AppResult.success(listOf(fact("warnings", "avoid in kidney disease", "b"))) }
            )
        )
        val result = service.search("Panadol")
        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts.first()
        assertEquals("warnings", conflict.field)
        // Both values stay available — the aggregator picks no winner.
        assertEquals(2, conflict.facts.size)
        assertTrue(conflict.facts.any { it.source.id == "a" })
        assertTrue(conflict.facts.any { it.source.id == "b" })
        assertTrue(KnowledgeWarning.CONFLICT_PRESENT in result.warnings)
    }

    // ---------- offline / timeout / retry ----------

    @Test
    fun `one unreachable source degrades gracefully with a warning`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { AppResult.success(listOf(fact("indications", "pain relief", "a"))) },
                FakeProvider("offline") { AppResult.error(ErrorCode.NETWORK, "connection refused") }
            ),
            attempts = 1
        )
        val result = service.search("Panadol")
        assertEquals(1, result.facts.size)
        assertTrue(result.failedSources.any { it.id == "offline" })
        assertTrue(KnowledgeWarning.SOURCE_UNAVAILABLE in result.warnings)
        assertTrue(KnowledgeWarning.PARTIAL_RESULTS in result.warnings)
    }

    @Test
    fun `all sources down still returns a usable result`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { throw java.io.IOException("offline") },
                FakeProvider("b") { AppResult.error(ErrorCode.NETWORK, "timeout") }
            ),
            attempts = 1
        )
        val result = service.search("Panadol")
        assertTrue(result.facts.isEmpty())
        assertEquals(2, result.failedSources.size)
        assertTrue(KnowledgeWarning.SOURCE_UNAVAILABLE in result.warnings)
    }

    @Test
    fun `a hanging source is cut off by the timeout policy`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("slow") {
                    delay(600_000)
                    AppResult.success(listOf(fact("indications", "never", "slow")))
                },
                FakeProvider("fast") { AppResult.success(listOf(fact("indications", "quick", "fast"))) }
            ),
            attempts = 1,
            timeoutMs = 1_000L
        )
        val result = service.search("Panadol")
        assertEquals(1, result.facts.size)
        assertEquals("quick", result.facts.first().value)
        assertTrue(result.failedSources.any { it.id == "slow" })
    }

    @Test
    fun `a flaky source is retried and recovers`() = runTest {
        var calls = 0
        val provider = object : DrugKnowledgeProvider {
            override val id = "flaky"
            override val displayName = "Flaky"
            override val sourceUrl = "https://flaky.example"
            override suspend fun fetchFacts(query: String): AppResult<List<DrugFact>> {
                calls++
                return if (calls == 1) AppResult.error(ErrorCode.NETWORK, "blip")
                else AppResult.success(listOf(fact("indications", "recovered", "flaky")))
            }
        }
        val service = DrugKnowledgeService(providers = listOf(provider), attempts = 2)
        val result = service.search("Panadol")
        assertEquals(1, result.facts.size)
        assertEquals("recovered", result.facts.first().value)
        assertEquals(2, calls)
        assertTrue(result.failedSources.isEmpty())
    }

    @Test
    fun `the disclaimer warning is always present`() = runTest {
        val service = DrugKnowledgeService(
            providers = listOf(
                FakeProvider("a") { AppResult.success(listOf(fact("indications", "x", "a"))) }
            )
        )
        assertTrue(KnowledgeWarning.NOT_MEDICAL_ADVICE in service.search("q").warnings)
    }

    @Test
    fun `google style general search is not a registered source`() = runTest {
        val service = DrugKnowledgeService(providers = emptyList())
        val names = service.availableSources().map { it.name.lowercase() }
        assertFalse(names.any { it.contains("google") || it.contains("web search") })
    }
}

package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates the registered knowledge providers through the network
 * policy (timeout + retries) and merges their outcomes.
 *
 * Providers run in parallel; one slow or offline source never blocks the
 * others — it is reported as an unavailable source instead.
 */
class DrugKnowledgeService(
    private val providers: List<DrugKnowledgeProvider>,
    private val attempts: Int = FetchPolicy.DEFAULT_ATTEMPTS,
    private val timeoutMs: Long = FetchPolicy.DEFAULT_TIMEOUT_MS
) {

    fun availableSources() = providers.filter { it.enabled }.map { it.asSource(retrievedAt = 0L) }

    suspend fun search(query: String): DrugKnowledgeResult = coroutineScope {
        val active = providers.filter { it.enabled }
        val outcomes = active.map { provider ->
            async {
                val fetchedAt = System.currentTimeMillis()
                val result = FetchPolicy.withRetry(attempts = attempts, timeoutMs = timeoutMs) {
                    provider.fetchFacts(query)
                }
                ProviderOutcome(source = provider.asSource(fetchedAt), result = result)
            }
        }.map { it.await() }
        DrugKnowledgeAggregator.merge(query, outcomes)
    }
}

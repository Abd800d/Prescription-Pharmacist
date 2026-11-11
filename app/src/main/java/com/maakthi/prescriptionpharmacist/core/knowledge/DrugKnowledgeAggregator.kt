package com.maakthi.prescriptionpharmacist.core.knowledge

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeConflict
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning

/**
 * Merges provider outcomes into one [DrugKnowledgeResult].
 *
 * Rules:
 * - every fact keeps its source attribution;
 * - identical values from several sources are de-duplicated;
 * - disagreeing values become a [DrugKnowledgeConflict] — **no winner is
 *   ever picked automatically**;
 * - failed sources are reported as warnings, the rest of the results stay.
 */
object DrugKnowledgeAggregator {

    fun merge(query: String, outcomes: List<ProviderOutcome>): DrugKnowledgeResult {
        val succeeded = outcomes.filter { it.result.isSuccess }
        val failed = outcomes.filter { !it.result.isSuccess }

        val facts = succeeded
            .flatMap { outcome -> outcome.result.getOrNull().orEmpty() }
            .map { fact -> fact.copy(value = fact.value.trim()) }
            .filter { it.value.isNotEmpty() }

        val deduped = facts
            .distinctBy { it.field to normalize(it.value) }

        val conflicts = deduped
            .groupBy { it.field }
            .mapNotNull { (field, fieldFacts) ->
                val byValue = fieldFacts.distinctBy { normalize(it.value) }
                if (byValue.size > 1) DrugKnowledgeConflict(field, byValue) else null
            }

        val warnings = buildSet {
            if (failed.isNotEmpty()) {
                add(KnowledgeWarning.SOURCE_UNAVAILABLE)
                if (succeeded.isNotEmpty()) add(KnowledgeWarning.PARTIAL_RESULTS)
            }
            if (conflicts.isNotEmpty()) add(KnowledgeWarning.CONFLICT_PRESENT)
            add(KnowledgeWarning.NOT_MEDICAL_ADVICE)
        }

        return DrugKnowledgeResult(
            query = query,
            facts = deduped,
            conflicts = conflicts,
            warnings = warnings,
            failedSources = failed.map { it.source }
        )
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[\\s\\-_.,؛,]+"), "").trim()
}

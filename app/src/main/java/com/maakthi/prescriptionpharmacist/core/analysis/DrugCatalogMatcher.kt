package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType

/**
 * Stage 9: matches an extracted name against the drug catalog.
 *
 * - Exact/normalized match → high score, canonical catalog record.
 * - Similar names (typos, OCR misreads, brand/generic confusion) → ranked
 *   alternatives.
 * - NEVER invents a record: when nothing matches, [MatchResult.matched] is
 *   null and the caller must mark the result uncertain.
 */
object DrugCatalogMatcher {

    /** Minimum score for a candidate to be shown as an alternative. */
    const val ALTERNATIVE_MIN_SCORE = 0.55f
    /** Minimum score for a fuzzy match to be accepted as the primary match. */
    const val MATCH_MIN_SCORE = 0.82f

    data class MatchCandidate(
        val drugId: String,
        val name: String,
        val scientificName: String?,
        val type: MedicationType,
        val score: Float
    )

    data class MatchResult(
        val matched: Drug?,
        val score: Float,
        val alternatives: List<MatchCandidate>
    )

    fun match(query: String, catalog: List<Drug>, maxAlternatives: Int = 3): MatchResult {
        val normalizedQuery = TextNormalizer.normalize(query)
        if (normalizedQuery.isEmpty() || catalog.isEmpty()) {
            return MatchResult(matched = null, score = 0f, alternatives = emptyList())
        }

        val scored = catalog.map { drug -> drug to bestScore(normalizedQuery, drug) }
            .sortedByDescending { it.second }

        val top = scored.firstOrNull { it.second >= MATCH_MIN_SCORE }
        val matched = top?.first
        val matchedScore = top?.second ?: 0f

        val alternatives = scored
            .filter { (drug, score) ->
                score >= ALTERNATIVE_MIN_SCORE &&
                    (matched == null || drug.id != matched.id)
            }
            .take(maxAlternatives)
            .map { (drug, score) ->
                MatchCandidate(
                    drugId = drug.id,
                    name = drug.name,
                    scientificName = drug.scientificName,
                    type = drug.type,
                    score = score
                )
            }

        return MatchResult(matched = matched, score = matchedScore, alternatives = alternatives)
    }

    private fun bestScore(normalizedQuery: String, drug: Drug): Float {
        val nameScore = TextNormalizer.similarity(normalizedQuery, drug.name)
        val sciScore = drug.scientificName
            ?.let { TextNormalizer.similarity(normalizedQuery, it) }
            ?: 0f
        val best = maxOf(nameScore, sciScore)
        // A brand trade name often embeds the generic ("Panadol Extra" vs
        // "Panadol"): containment is a strong signal.
        val dn = TextNormalizer.normalize(drug.name)
        val contained = dn.isNotEmpty() &&
            (normalizedQuery.contains(dn) || dn.contains(normalizedQuery))
        return if (contained && best < 0.95f) maxOf(best, 0.85f) else best
    }
}

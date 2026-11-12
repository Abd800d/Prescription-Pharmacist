package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * Identity of an official drug-information source (openFDA, a ministry of
 * health API, a local drug authority…). Every fact carries its source so the
 * pharmacist always sees where a piece of information came from.
 */
@Serializable
data class DrugSourceInfo(
    /** Stable connector id, e.g. "openfda", "authority". */
    val id: String,
    /** Human-readable name, e.g. "openFDA (US FDA)". */
    val name: String,
    /** Link to the source / its API documentation. */
    val url: String,
    /** When the information was retrieved (epoch millis). */
    val retrievedAt: Long,
    /** True for official regulatory sources. */
    val isOfficial: Boolean = true
)

/**
 * One piece of drug information, always attributed to exactly one source.
 * [field] uses the drug-database column names (indications, warnings, …).
 */
@Serializable
data class DrugFact(
    val field: String,
    val value: String,
    val source: DrugSourceInfo
)

/** Two or more sources disagree about the same field. The system NEVER picks a winner. */
data class DrugKnowledgeConflict(
    val field: String,
    /** One entry per distinct value, each with its own source. */
    val facts: List<DrugFact>
)

/** Non-fatal conditions the pharmacist must see. */
enum class KnowledgeWarning {
    /** At least one source could not be reached (offline, timeout, error). */
    SOURCE_UNAVAILABLE,

    /** Results are incomplete because some sources failed. */
    PARTIAL_RESULTS,

    /** Sources disagree — the pharmacist must review manually. */
    CONFLICT_PRESENT,

    /** Reference information only — never a substitute for professional judgement. */
    NOT_MEDICAL_ADVICE
}

/**
 * Aggregated search outcome. Facts keep their attribution; conflicts are
 * reported, never resolved automatically.
 */
data class DrugKnowledgeResult(
    val query: String,
    val facts: List<DrugFact>,
    val conflicts: List<DrugKnowledgeConflict>,
    val warnings: Set<KnowledgeWarning>,
    /** Sources that could not be reached (name shown to the user). */
    val failedSources: List<DrugSourceInfo>
)

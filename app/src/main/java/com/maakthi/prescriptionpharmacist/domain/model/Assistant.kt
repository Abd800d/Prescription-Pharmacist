package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * RAG assistant models: evidence retrieved from the local drug database and
 * the official knowledge sources, the model answer with citations that can
 * ONLY point at provided evidence, and the user's local Q&A log.
 */

/** One retrieved piece of evidence with mandatory attribution. */
@Serializable
data class EvidenceItem(
    /** 1-based id; the model cites it as [1], [2], … */
    val id: Int,
    val text: String,
    val sourceName: String,
    val sourceUrl: String = "",
    val sourceDate: String = ""
)

/** A citation shown to the user — always taken from real evidence. */
@Serializable
data class AssistantCitation(
    val sourceName: String,
    val sourceUrl: String = "",
    val sourceDate: String = ""
)

/** Everything retrieved for one question. */
data class EvidencePack(
    val items: List<EvidenceItem>,
    val matchedDrugs: List<String> = emptyList(),
    val usedKnowledge: Boolean = false,
    val conflictsNoted: Boolean = false
) {
    val insufficient: Boolean get() = items.isEmpty()
}

/** The final, guarded answer shown to the user and stored in their log. */
@Serializable
data class AssistantAnswer(
    val reply: String,
    val citations: List<AssistantCitation> = emptyList(),
    val insufficient: Boolean = false
)

/** One logged Q&A pair — stored locally, deletable by the user. */
@Serializable
data class AssistantLogEntry(
    val id: String,
    val question: String,
    val answer: String,
    val citations: List<AssistantCitation> = emptyList(),
    val insufficient: Boolean = false,
    val language: String = "en",
    val timestamp: Long
)

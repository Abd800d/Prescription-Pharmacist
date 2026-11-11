package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AssistantAnswer
import com.maakthi.prescriptionpharmacist.domain.model.AssistantLogEntry
import com.maakthi.prescriptionpharmacist.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * RAG drug assistant: answers come from the local drug database + official
 * sources first (retrieval), and only from the retrieved evidence (with
 * source name + link + date). When evidence is insufficient the answer is an
 * explicit refusal — never a guess. Q&A pairs are logged locally on-device
 * and the user can delete the log at any time.
 */
interface AssistantRepository {

    suspend fun ask(question: String, history: List<ChatMessage>): AppResult<AssistantAnswer>

    /** The user's local Q&A log (on-device only). */
    fun observeLog(): Flow<List<AssistantLogEntry>>

    /** Deletes the whole conversation log. */
    suspend fun clearLog()
}

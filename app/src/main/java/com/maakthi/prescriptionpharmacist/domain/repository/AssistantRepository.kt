package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ChatMessage

/**
 * AI pharmacist assistant — conversational help through the secure backend
 * (`POST {baseUrl}/chat`). No API keys live in the app.
 */
interface AssistantRepository {
    suspend fun sendMessage(
        history: List<ChatMessage>,
        message: String
    ): AppResult<String>
}

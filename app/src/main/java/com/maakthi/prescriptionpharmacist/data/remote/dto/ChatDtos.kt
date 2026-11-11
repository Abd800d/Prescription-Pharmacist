package com.maakthi.prescriptionpharmacist.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * RAG chat contract with the secure backend:
 * the app sends the question + retrieved evidence (with attribution); the
 * server answers FROM that evidence only and returns citations that can only
 * point at the provided items.
 */
@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val evidence: List<EvidenceDto> = emptyList(),
    val language: String = "en"
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class EvidenceDto(
    val id: Int,
    val text: String,
    val sourceName: String,
    val sourceUrl: String = "",
    val sourceDate: String = ""
)

@Serializable
data class CitationDto(
    val sourceName: String,
    val sourceUrl: String = "",
    val sourceDate: String = ""
)

@Serializable
data class ChatResponseDto(
    val reply: String? = null,
    val citations: List<CitationDto> = emptyList(),
    val error: ErrorDto? = null
)

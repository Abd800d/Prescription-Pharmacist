package com.maakthi.prescriptionpharmacist.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequestDto(
    val messages: List<ChatMessageDto>
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponseDto(
    val reply: String? = null,
    val error: ErrorDto? = null
)

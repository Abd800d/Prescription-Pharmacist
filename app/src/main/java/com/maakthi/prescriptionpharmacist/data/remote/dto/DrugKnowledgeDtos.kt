package com.maakthi.prescriptionpharmacist.data.remote.dto

import kotlinx.serialization.Serializable

/** Query sent to the backend drug-information endpoint. */
@Serializable
data class DrugKnowledgeQueryDto(val query: String)

/** One sourced fact returned by the backend. */
@Serializable
data class DrugKnowledgeFactDto(
    val field: String,
    val value: String,
    val sourceName: String,
    val sourceUrl: String,
    val retrievedAt: Long = 0L
)

/**
 * Backend response of `POST /druginfo/search`. The backend aggregates the
 * official authority APIs (ministry of health, local drug authority…) and
 * keeps all API keys on the server.
 */
@Serializable
data class DrugKnowledgeResponseDto(
    val facts: List<DrugKnowledgeFactDto> = emptyList(),
    val note: String? = null
)

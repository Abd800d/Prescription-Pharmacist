package com.maakthi.prescriptionpharmacist.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire format of the secure analysis backend. See server/README.md for the
 * full API contract.
 */
@Serializable
data class AnalysisResponseDto(
    val requestId: String? = null,
    val language: String? = null,
    val notes: String? = null,
    val medications: List<MedicationDto> = emptyList(),
    val error: ErrorDto? = null
)

@Serializable
data class MedicationDto(
    val name: String,
    val scientificName: String? = null,
    val type: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val confidence: Double = 0.0,
    val rawText: String? = null
)

@Serializable
data class ErrorDto(
    val code: String? = null,
    val message: String? = null
)

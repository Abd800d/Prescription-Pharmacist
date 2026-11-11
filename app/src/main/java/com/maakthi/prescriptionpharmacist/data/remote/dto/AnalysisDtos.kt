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

// ---------- staged pipeline wire formats ----------

/** Response of POST /ocr — raw transcription of the prescription image. */
@Serializable
data class OcrResponseDto(
    val requestId: String? = null,
    val language: String? = null,
    val text: String = "",
    val lines: List<OcrLineDto> = emptyList(),
    val error: ErrorDto? = null
)

@Serializable
data class OcrLineDto(
    val text: String = "",
    val confidence: Double = 1.0
)

/** Request of POST /extract — OCR text to structure into medication hints. */
@Serializable
data class ExtractRequestDto(
    val text: String
)

/** Response of POST /extract. */
@Serializable
data class ExtractResponseDto(
    val hints: List<MedicationHintDto> = emptyList(),
    val error: ErrorDto? = null
)

@Serializable
data class MedicationHintDto(
    val name: String = "",
    val scientificName: String? = null,
    val type: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val rawText: String? = null,
    val unclear: Boolean = false,
    val alternatives: List<String> = emptyList()
)

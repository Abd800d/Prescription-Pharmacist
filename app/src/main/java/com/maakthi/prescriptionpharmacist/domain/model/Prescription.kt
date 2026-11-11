package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * A prescription saved in the local database after pharmacist review.
 */
@Serializable
data class Prescription(
    val id: String,
    val imagePath: String,
    val createdAt: Long,
    val status: PrescriptionStatus = PrescriptionStatus.PENDING,
    val notes: String? = null,
    val analysisLanguage: String? = null,
    val remoteRequestId: String? = null,
    val medications: List<Medication> = emptyList()
)

@Serializable
data class Medication(
    val id: String,
    val prescriptionId: String,
    val name: String,
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val dose: String? = null,
    val route: String? = null,
    val duration: String? = null,
    val confidence: Float = 0f,
    val rawText: String? = null,
    val isUncertain: Boolean = false,
    val alternatives: List<MedicationAlternative> = emptyList(),
    val confirmedByPharmacist: Boolean = false
)

@Serializable
enum class PrescriptionStatus { PENDING, CONFIRMED, REJECTED }

/** Confidence tier for a saved medication, same thresholds as [MedicationCandidate]. */
val Medication.confidenceLevel: ConfidenceLevel
    get() = confidenceLevelOf(confidence)

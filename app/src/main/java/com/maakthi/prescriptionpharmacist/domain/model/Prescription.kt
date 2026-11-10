package com.maakthi.prescriptionpharmacist.domain.model

/**
 * A prescription saved in the local database after pharmacist review.
 */
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

data class Medication(
    val id: String,
    val prescriptionId: String,
    val name: String,
    val scientificName: String? = null,
    val type: MedicationType = MedicationType.UNKNOWN,
    val strength: String? = null,
    val form: String? = null,
    val confidence: Float = 0f,
    val rawText: String? = null,
    val confirmedByPharmacist: Boolean = false
)

enum class PrescriptionStatus { PENDING, CONFIRMED, REJECTED }

/** Confidence tier for a saved medication, same thresholds as [MedicationCandidate]. */
val Medication.confidenceLevel: ConfidenceLevel
    get() = when {
        confidence >= MedicationCandidate.HIGH_CONFIDENCE_THRESHOLD -> ConfidenceLevel.HIGH
        confidence >= MedicationCandidate.MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

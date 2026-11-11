package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import javax.inject.Inject

/**
 * Saves a reviewed prescription locally (plus its image in the media library,
 * dated and attributed to the saving user) and — when Firebase is configured —
 * backs it up to cloud storage. Local save always succeeds first so no work is
 * ever lost; cloud backup is best-effort.
 */
class SavePrescriptionUseCase @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository,
    private val backupRepository: BackupRepository,
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(prescription: Prescription, imageBytes: ByteArray?) {
        // stamp the change time so sync can detect local edits
        val saved = prescription.copy(updatedAt = System.currentTimeMillis())
        prescriptionRepository.savePrescription(saved)
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            mediaRepository.savePrescriptionImage(
                imageBytes = imageBytes,
                ownerName = "",
                capturedAt = prescription.createdAt
            )
        }
        if (backupRepository.isAvailable()) {
            backupRepository.backupPrescription(saved, imageBytes)
        }
    }
}

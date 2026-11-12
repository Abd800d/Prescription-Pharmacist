package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import javax.inject.Inject

/** Updates the pharmacist decision (confirm / reject) for a saved prescription. */
class UpdatePrescriptionStatusUseCase @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository
) {
    suspend operator fun invoke(id: String, status: PrescriptionStatus) =
        prescriptionRepository.updateStatus(id, status)
}

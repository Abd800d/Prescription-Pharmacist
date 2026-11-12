package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import javax.inject.Inject

/** Deletes a prescription and its medications. */
class DeletePrescriptionUseCase @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository
) {
    suspend operator fun invoke(id: String) = prescriptionRepository.deletePrescription(id)
}

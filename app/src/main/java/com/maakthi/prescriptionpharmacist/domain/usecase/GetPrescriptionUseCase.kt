package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams a single prescription (with its medications) by id. */
class GetPrescriptionUseCase @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository
) {
    operator fun invoke(id: String): Flow<Prescription?> =
        prescriptionRepository.observePrescription(id)
}

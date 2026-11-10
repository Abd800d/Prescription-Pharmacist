package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams all saved prescriptions, newest first. */
class ObservePrescriptionsUseCase @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository
) {
    operator fun invoke(): Flow<List<Prescription>> = prescriptionRepository.observePrescriptions()
}

package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Local storage of reviewed prescriptions (Room).
 */
interface PrescriptionRepository {
    fun observePrescriptions(): Flow<List<Prescription>>
    fun observePrescription(id: String): Flow<Prescription?>
    suspend fun getPrescription(id: String): Prescription?
    suspend fun savePrescription(prescription: Prescription)
    suspend fun updateStatus(id: String, status: PrescriptionStatus)
    suspend fun deletePrescription(id: String)
}

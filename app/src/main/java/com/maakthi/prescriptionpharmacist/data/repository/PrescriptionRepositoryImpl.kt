package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrescriptionRepositoryImpl @Inject constructor(
    private val dao: PrescriptionDao
) : PrescriptionRepository {

    override fun observePrescriptions(): Flow<List<Prescription>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePrescription(id: String): Flow<Prescription?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getPrescription(id: String): Prescription? =
        dao.getById(id)?.toDomain()

    override suspend fun savePrescription(prescription: Prescription) {
        dao.upsert(
            prescription.toEntity(),
            prescription.medications.map { it.toEntity() }
        )
    }

    override suspend fun updateStatus(id: String, status: PrescriptionStatus) {
        dao.updateStatus(id, status.name)
    }

    override suspend fun deletePrescription(id: String) {
        dao.deleteWithMedications(id)
    }
}

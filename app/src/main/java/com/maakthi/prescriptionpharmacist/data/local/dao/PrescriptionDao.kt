package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionWithMedications
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {

    @Transaction
    @Query("SELECT * FROM prescriptions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PrescriptionWithMedications>>

    @Transaction
    @Query("SELECT * FROM prescriptions WHERE id = :id")
    fun observeById(id: String): Flow<PrescriptionWithMedications?>

    @Transaction
    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getById(id: String): PrescriptionWithMedications?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: PrescriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<MedicationEntity>)

    @Query("UPDATE prescriptions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM medications WHERE prescriptionId = :prescriptionId")
    suspend fun deleteMedications(prescriptionId: String)

    @Query("DELETE FROM prescriptions WHERE id = :id")
    suspend fun deletePrescription(id: String)

    @Transaction
    suspend fun upsert(prescription: PrescriptionEntity, medications: List<MedicationEntity>) {
        insertPrescription(prescription)
        deleteMedications(prescription.id)
        insertMedications(medications)
    }

    @Transaction
    suspend fun deleteWithMedications(id: String) {
        deleteMedications(id)
        deletePrescription(id)
    }
}

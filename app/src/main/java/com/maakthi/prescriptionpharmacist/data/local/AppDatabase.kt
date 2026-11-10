package com.maakthi.prescriptionpharmacist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity

@Database(
    entities = [PrescriptionEntity::class, MedicationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prescriptionDao(): PrescriptionDao

    companion object {
        const val NAME = "prescription_pharmacist.db"
    }
}

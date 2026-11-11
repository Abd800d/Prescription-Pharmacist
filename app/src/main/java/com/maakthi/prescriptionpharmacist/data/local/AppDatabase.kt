package com.maakthi.prescriptionpharmacist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugAuditDao
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.dao.ProductImageDao
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugAuditEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.MedicationEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.PrescriptionEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.ProductImageEntity

@Database(
    entities = [
        PrescriptionEntity::class,
        MedicationEntity::class,
        DrugEntity::class,
        DrugAuditEntity::class,
        ProductImageEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun drugDao(): DrugDao
    abstract fun drugAuditDao(): DrugAuditDao
    abstract fun productImageDao(): ProductImageDao

    companion object {
        const val NAME = "prescription_pharmacist.db"
    }
}

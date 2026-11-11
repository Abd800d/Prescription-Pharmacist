package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugAuditDao {

    @Query("SELECT * FROM drug_audit WHERE drugId = :drugId ORDER BY timestamp DESC")
    fun observeByDrug(drugId: String): Flow<List<DrugAuditEntity>>

    @Query("SELECT * FROM drug_audit WHERE drugId = :drugId ORDER BY timestamp DESC")
    suspend fun byDrug(drugId: String): List<DrugAuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DrugAuditEntity)
}

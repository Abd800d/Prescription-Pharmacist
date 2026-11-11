package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.TransferLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferLogDao {

    @Query("SELECT * FROM transfer_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransferLogEntity>>

    @Query("SELECT * FROM transfer_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<TransferLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TransferLogEntity)
}

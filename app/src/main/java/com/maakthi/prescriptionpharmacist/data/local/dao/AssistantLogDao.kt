package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.AssistantLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantLogDao {

    @Query("SELECT * FROM assistant_log ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<AssistantLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AssistantLogEntity)

    @Query("DELETE FROM assistant_log")
    suspend fun clear()
}

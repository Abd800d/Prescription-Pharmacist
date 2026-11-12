package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.AuditEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditEventEntity>>

    @Query("DELETE FROM audit_log")
    suspend fun clear()
}

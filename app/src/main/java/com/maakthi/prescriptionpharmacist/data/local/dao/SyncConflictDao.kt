package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun getById(id: String): SyncConflictEntity?

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun delete(id: String)
}

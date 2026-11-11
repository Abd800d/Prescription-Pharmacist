package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.MediaAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {

    @Query("SELECT * FROM media_assets ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE kind = :kind ORDER BY capturedAt DESC")
    fun observeByKind(kind: String): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun getById(id: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun pending(): List<MediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaAssetEntity)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media_assets")
    suspend fun clear()
}

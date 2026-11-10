package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Query("SELECT * FROM drugs ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs WHERE id = :id")
    suspend fun getById(id: String): DrugEntity?

    @Query(
        "SELECT * FROM drugs WHERE name LIKE '%' || :query || '%' " +
            "OR scientificName LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC"
    )
    suspend fun search(query: String): List<DrugEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(drug: DrugEntity)

    @Query("DELETE FROM drugs WHERE id = :id")
    suspend fun delete(id: String)
}

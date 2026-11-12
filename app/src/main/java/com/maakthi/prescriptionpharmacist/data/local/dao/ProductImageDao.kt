package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductImageDao {

    @Query("SELECT * FROM product_images ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProductImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ProductImageEntity)

    @Query("DELETE FROM product_images WHERE id = :id")
    suspend fun delete(id: String)
}

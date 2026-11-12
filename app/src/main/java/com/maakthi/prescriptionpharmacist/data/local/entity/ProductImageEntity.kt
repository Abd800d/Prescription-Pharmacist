package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_images")
data class ProductImageEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val title: String,
    val createdAt: Long
)

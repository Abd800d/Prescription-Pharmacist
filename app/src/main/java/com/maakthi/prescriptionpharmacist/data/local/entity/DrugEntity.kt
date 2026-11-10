package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drugs")
data class DrugEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scientificName: String?,
    val type: String,
    val strength: String?,
    val form: String?,
    val manufacturer: String?,
    val indications: String?,
    val dosage: String?,
    val warnings: String?,
    val notes: String?
)

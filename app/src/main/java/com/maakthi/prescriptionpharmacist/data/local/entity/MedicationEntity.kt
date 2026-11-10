package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("prescriptionId")]
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val prescriptionId: String,
    val name: String,
    val scientificName: String?,
    val type: String,
    val strength: String?,
    val form: String?,
    val confidence: Float,
    val rawText: String?,
    val confirmedByPharmacist: Boolean
)

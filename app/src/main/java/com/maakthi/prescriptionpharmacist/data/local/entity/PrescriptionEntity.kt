package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescriptions")
data class PrescriptionEntity(
    @PrimaryKey val id: String,
    val imagePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val notes: String?,
    val analysisLanguage: String?,
    val remoteRequestId: String?
)

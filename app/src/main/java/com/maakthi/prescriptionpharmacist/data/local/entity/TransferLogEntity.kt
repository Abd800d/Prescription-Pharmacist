package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One entry of the import/export history. */
@Entity(
    tableName = "transfer_log",
    indices = [Index(value = ["timestamp"])]
)
data class TransferLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val format: String,
    val fileName: String,
    val rowsOk: Int,
    val rowsFailed: Int,
    val encrypted: Boolean,
    val performedBy: String,
    val timestamp: Long
)

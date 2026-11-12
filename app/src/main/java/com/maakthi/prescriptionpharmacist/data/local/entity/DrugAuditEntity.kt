package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One entry of the drug change history (audit log). */
@Entity(
    tableName = "drug_audit",
    indices = [
        Index(value = ["drugId"]),
        Index(value = ["timestamp"])
    ]
)
data class DrugAuditEntity(
    @PrimaryKey val id: String,
    val drugId: String,
    val action: String,
    val changes: String,
    val performedBy: String,
    val timestamp: Long
)

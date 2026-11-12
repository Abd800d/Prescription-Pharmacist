package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A pending sync conflict: both the phone and the server changed the same
 * record. Both versions are kept until the user explicitly chooses.
 */
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val label: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localJson: String,
    val remoteJson: String,
    val detectedAt: Long
)

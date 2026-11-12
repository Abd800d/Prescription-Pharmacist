package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved image (prescription or product). Prescription images store the
 * owner name encrypted; image bytes are encrypted at rest for prescription
 * assets.
 */
@Entity(
    tableName = "media_assets",
    indices = [Index(value = ["kind"]), Index(value = ["syncState"]), Index(value = ["capturedAt"])]
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val ownerId: String,
    val ownerNameEncrypted: String,
    val title: String,
    val capturedAt: Long,
    val localPath: String,
    val remotePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val syncState: String,
    val lastSyncAt: Long
)

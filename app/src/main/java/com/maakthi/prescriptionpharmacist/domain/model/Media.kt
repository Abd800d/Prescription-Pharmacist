package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/**
 * Media library models: prescription images and pharmaceutical product
 * images, stored locally (encrypted at rest) and synced to secure per-user
 * storage when the user enables sync.
 */

enum class MediaKind { PRESCRIPTION_IMAGE, PRODUCT_IMAGE }

enum class SyncState {
    /** Local-only copy (sync disabled or offline). */
    LOCAL,
    /** Waiting to be uploaded when sync is on and a connection is available. */
    PENDING,
    /** Uploaded to secure storage. */
    SYNCED,
    /** Last upload attempt failed — retried later. */
    FAILED
}

/**
 * One saved image. Prescription images record the prescription date and the
 * saving user; product images carry a title. [ownerName] is stored encrypted
 * for prescription images (sensitive data).
 */
@Serializable
data class MediaAsset(
    val id: String,
    val kind: MediaKind,
    val ownerId: String,
    val ownerName: String,
    val title: String = "",
    val capturedAt: Long,
    val localPath: String,
    val remotePath: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0,
    val encrypted: Boolean = false,
    val syncState: SyncState = SyncState.LOCAL,
    val lastSyncAt: Long = 0
)

/** What the sync engine should do with one asset right now. */
enum class MediaSyncAction {
    UPLOAD,
    WAIT_FOR_NETWORK,
    WAIT_FOR_SYNC_TOGGLE,
    RETRY_LATER,
    NOTHING
}

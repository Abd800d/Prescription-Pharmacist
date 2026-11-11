package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/** What is being synchronized. */
enum class SyncEntityType { DRUG, PRESCRIPTION, IMAGE }

/** One record in transit between the phone and the server. */
@Serializable
data class SyncRecord(
    val id: String,
    val updatedAt: Long,
    val payloadJson: String = ""
)

/** How a record's local and remote versions relate. */
enum class ChangeKind {
    NONE,
    LOCAL_CHANGED,
    REMOTE_CHANGED,
    BOTH_CHANGED
}

/** A conflict the user must resolve — never auto-decided. */
@Serializable
data class SyncConflict(
    val id: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val label: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localJson: String,
    val remoteJson: String,
    val detectedAt: Long
)

enum class ConflictChoice { KEEP_LOCAL, KEEP_REMOTE }

/** Online / offline state shown in the sync center. */
enum class ConnectionState { ONLINE, OFFLINE, UNKNOWN }

/** Result of one sync run. */
data class SyncReport(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    val interrupted: Boolean = false,
    val completedAt: Long = 0
) {
    val success: Boolean get() = !interrupted
}

/** Everything the sync center shows. */
data class SyncStatus(
    val connection: ConnectionState = ConnectionState.UNKNOWN,
    val lastSyncAt: Long = 0,
    val lastBackupAt: Long = 0,
    val pendingConflicts: Int = 0,
    val autoBackupEnabled: Boolean = true,
    val busy: Boolean = false
)

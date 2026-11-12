package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict
import com.maakthi.prescriptionpharmacist.domain.model.SyncReport
import com.maakthi.prescriptionpharmacist.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Real phone↔server synchronization, offline-first:
 *
 * - drugs, prescriptions and images sync in both directions (upserts only —
 *   sync NEVER deletes user data);
 * - conflicts (both sides changed) are surfaced to the user and resolved
 *   only by their explicit choice — user edits are never replaced silently;
 * - "sync now", last sync time and connection status are always visible;
 * - manual and automatic ENCRYPTED backups, plus restore (from file or cloud).
 */
interface SyncRepository {

    /** Runs a full sync: drugs + prescriptions + images, then auto-backup. */
    suspend fun syncNow(): AppResult<SyncReport>

    fun observeStatus(): Flow<SyncStatus>

    fun observeConflicts(): Flow<List<SyncConflict>>

    /** Resolves one conflict by the user's explicit choice. */
    suspend fun resolveConflict(conflictId: String, choice: ConflictChoice): AppResult<Unit>

    /** Manual encrypted backup (local + cloud). */
    suspend fun backupNow(): AppResult<Unit>

    /** Enables/disables the automatic encrypted backup. */
    suspend fun setAutoBackupEnabled(enabled: Boolean)

    /** Stores the backup passphrase (encrypted with the device key). */
    suspend fun setBackupPassphrase(passphrase: String)

    suspend fun hasBackupPassphrase(): Boolean

    /** Restores the latest encrypted backup from the cloud. */
    suspend fun restoreFromCloud(): AppResult<Unit>

    /** Restores an encrypted backup file (.ppbak). */
    suspend fun restoreFromFile(bytes: ByteArray, passphrase: String): AppResult<Unit>
}

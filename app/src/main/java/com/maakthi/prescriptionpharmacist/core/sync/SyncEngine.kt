package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.ChangeKind
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import com.maakthi.prescriptionpharmacist.domain.model.SyncReport
import java.io.IOException

/**
 * The transport (server) side. Implementations throw [IOException] on
 * connection loss — the engine treats that as an interruption, never as data
 * loss.
 */
interface SyncGateway {
    suspend fun push(type: SyncEntityType, records: List<SyncRecord>)
    suspend fun pull(type: SyncEntityType, since: Long): List<SyncRecord>
}

/**
 * The local (offline-first) side — Room in production, fakes in tests.
 */
interface SyncLocalStore {
    suspend fun lastSyncAt(type: SyncEntityType): Long
    suspend fun setLastSyncAt(type: SyncEntityType, at: Long)
    suspend fun localRecord(type: SyncEntityType, id: String): SyncRecord?
    /** Local records changed after [since] (the push queue). */
    suspend fun dirtyRecords(type: SyncEntityType, since: Long): List<SyncRecord>
    /** Applies a remote version locally (upsert — never deletes). */
    suspend fun applyRemote(type: SyncEntityType, record: SyncRecord)
    /** Preserves both versions and flags the conflict for the user. */
    suspend fun recordConflict(type: SyncEntityType, id: String, local: SyncRecord, remote: SyncRecord)
    /** Records the user's explicit resolution and stores the winner. */
    suspend fun applyResolution(type: SyncEntityType, id: String, winning: SyncRecord)
}

/**
 * Offline-first sync engine:
 *
 * 1. PUSH local changes (upserts only — sync never deletes data);
 * 2. PULL remote changes;
 * 3. conflicts (both sides changed) are stored for the user to resolve —
 *    user edits are NEVER replaced silently;
 * 4. the per-type clock advances ONLY when a full pass completes.
 *
 * A dropped connection mid-run leaves the clocks untouched, so the next run
 * simply resumes (uploads are idempotent upserts). Pure and unit-tested,
 * including the internet-interruption scenario.
 */
class SyncEngine(
    private val gateway: SyncGateway,
    private val store: SyncLocalStore
) {

    suspend fun syncType(type: SyncEntityType, now: Long): SyncReport {
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        return try {
            val lastSync = store.lastSyncAt(type)

            // ---- 1) push local changes ----
            val dirty = store.dirtyRecords(type, lastSync)
            if (dirty.isNotEmpty()) {
                gateway.push(type, dirty)
                pushed = dirty.size
            }

            // ---- 2) pull remote changes ----
            val remote = gateway.pull(type, lastSync)
            remote.forEach { record ->
                val local = store.localRecord(type, record.id)
                when (ChangeDetector.detect(local?.updatedAt, record.updatedAt, lastSync)) {
                    ChangeKind.NONE -> Unit
                    ChangeKind.LOCAL_CHANGED -> Unit // pushed above
                    ChangeKind.REMOTE_CHANGED -> {
                        store.applyRemote(type, record)
                        pulled++
                    }
                    ChangeKind.BOTH_CHANGED -> {
                        if (local != null) {
                            store.recordConflict(type, record.id, local, record)
                            conflicts++
                        }
                    }
                }
            }

            // ---- 3) full pass completed → advance the clock ----
            store.setLastSyncAt(type, now)
            SyncReport(pushed = pushed, pulled = pulled, conflicts = conflicts, completedAt = now)
        } catch (e: IOException) {
            // Interruption: keep the clock, keep the data, retry later.
            SyncReport(pushed = pushed, pulled = pulled, conflicts = conflicts, interrupted = true)
        }
    }

    suspend fun syncAll(types: List<SyncEntityType>, now: Long): SyncReport {
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var interrupted = false
        types.forEach { type ->
            val report = syncType(type, now)
            pushed += report.pushed
            pulled += report.pulled
            conflicts += report.conflicts
            if (report.interrupted) {
                interrupted = true
                return@forEach
            }
        }
        return SyncReport(
            pushed = pushed,
            pulled = pulled,
            conflicts = conflicts,
            interrupted = interrupted,
            completedAt = if (interrupted) 0 else now
        )
    }
}

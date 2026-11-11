package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.ChangeKind

/**
 * Decides how a local record and its remote copy relate, based on their
 * timestamps versus the last successful sync. Pure and unit-tested.
 *
 * The safety rule: when BOTH sides changed since the last sync the result is
 * ALWAYS [ChangeKind.BOTH_CHANGED] — a conflict for the user to resolve.
 * User edits are never silently replaced.
 */
object ChangeDetector {

    fun detect(
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long?,
        lastSyncAt: Long
    ): ChangeKind {
        val localExists = localUpdatedAt != null
        val remoteExists = remoteUpdatedAt != null
        val localChanged = localExists && localUpdatedAt > lastSyncAt
        val remoteChanged = remoteExists && remoteUpdatedAt > lastSyncAt

        return when {
            localExists && !remoteExists -> ChangeKind.LOCAL_CHANGED   // push new local
            !localExists && remoteExists -> ChangeKind.REMOTE_CHANGED  // pull new remote
            !localExists && !remoteExists -> ChangeKind.NONE
            localChanged && remoteChanged -> ChangeKind.BOTH_CHANGED
            localChanged -> ChangeKind.LOCAL_CHANGED
            remoteChanged -> ChangeKind.REMOTE_CHANGED
            else -> ChangeKind.NONE
        }
    }
}

package com.maakthi.prescriptionpharmacist.core.media

import com.maakthi.prescriptionpharmacist.domain.model.MediaSyncAction
import com.maakthi.prescriptionpharmacist.domain.model.SyncState

/**
 * Sync state machine: decides what to do with each asset. Pure and
 * unit-tested. The local copy is ALWAYS kept — upload only ever adds a
 * remote copy; nothing is deleted by syncing.
 */
object SyncPlanner {

    /** Delay before retrying a failed upload (5 minutes). */
    const val RETRY_BACKOFF_MILLIS = 5 * 60 * 1000L

    fun decide(
        syncState: SyncState,
        syncEnabled: Boolean,
        isOnline: Boolean,
        now: Long,
        lastAttemptAt: Long = 0
    ): MediaSyncAction {
        if (syncState == SyncState.SYNCED) return MediaSyncAction.NOTHING
        if (!syncEnabled) return MediaSyncAction.WAIT_FOR_SYNC_TOGGLE
        return when (syncState) {
            SyncState.LOCAL, SyncState.PENDING ->
                if (isOnline) MediaSyncAction.UPLOAD else MediaSyncAction.WAIT_FOR_NETWORK
            SyncState.FAILED ->
                when {
                    !isOnline -> MediaSyncAction.WAIT_FOR_NETWORK
                    now - lastAttemptAt >= RETRY_BACKOFF_MILLIS -> MediaSyncAction.UPLOAD
                    else -> MediaSyncAction.RETRY_LATER
                }
            SyncState.SYNCED -> MediaSyncAction.NOTHING
        }
    }

    /** State after a successful upload. */
    fun afterUpload(): SyncState = SyncState.SYNCED

    /** State after a failed upload — retried later, local copy intact. */
    fun afterFailure(): SyncState = SyncState.FAILED

    /**
     * Toggling sync ON turns local-only copies into pending uploads;
     * toggling it OFF never touches already-uploaded copies.
     */
    fun onSyncToggled(enabled: Boolean, current: SyncState): SyncState = when {
        enabled && current == SyncState.LOCAL -> SyncState.PENDING
        else -> current
    }
}

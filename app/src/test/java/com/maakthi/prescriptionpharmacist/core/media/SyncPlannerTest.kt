package com.maakthi.prescriptionpharmacist.core.media

import com.maakthi.prescriptionpharmacist.domain.model.MediaSyncAction
import com.maakthi.prescriptionpharmacist.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sync state machine: local-first, upload when possible, never delete. */
class SyncPlannerTest {

    @Test
    fun `synced assets are never re-uploaded`() {
        assertEquals(
            MediaSyncAction.NOTHING,
            SyncPlanner.decide(SyncState.SYNCED, syncEnabled = true, isOnline = true, now = 0)
        )
    }

    @Test
    fun `sync disabled waits for the toggle`() {
        assertEquals(
            MediaSyncAction.WAIT_FOR_SYNC_TOGGLE,
            SyncPlanner.decide(SyncState.PENDING, syncEnabled = false, isOnline = true, now = 0)
        )
    }

    @Test
    fun `pending uploads when online`() {
        assertEquals(
            MediaSyncAction.UPLOAD,
            SyncPlanner.decide(SyncState.PENDING, syncEnabled = true, isOnline = true, now = 0)
        )
    }

    @Test
    fun `offline waits for the network (local copy intact)`() {
        assertEquals(
            MediaSyncAction.WAIT_FOR_NETWORK,
            SyncPlanner.decide(SyncState.PENDING, syncEnabled = true, isOnline = false, now = 0)
        )
        assertEquals(
            MediaSyncAction.WAIT_FOR_NETWORK,
            SyncPlanner.decide(SyncState.FAILED, syncEnabled = true, isOnline = false, now = Long.MAX_VALUE)
        )
    }

    @Test
    fun `failures retry after the backoff`() {
        val lastAttempt = 1_000L
        assertEquals(
            MediaSyncAction.RETRY_LATER,
            SyncPlanner.decide(SyncState.FAILED, syncEnabled = true, isOnline = true, now = lastAttempt + 60_000, lastAttemptAt = lastAttempt)
        )
        assertEquals(
            MediaSyncAction.UPLOAD,
            SyncPlanner.decide(SyncState.FAILED, syncEnabled = true, isOnline = true, now = lastAttempt + SyncPlanner.RETRY_BACKOFF_MILLIS, lastAttemptAt = lastAttempt)
        )
    }

    @Test
    fun `toggling sync on marks local copies for upload`() {
        assertEquals(SyncState.PENDING, SyncPlanner.onSyncToggled(true, SyncState.LOCAL))
        assertEquals(SyncState.PENDING, SyncPlanner.onSyncToggled(true, SyncState.PENDING))
    }

    @Test
    fun `toggling sync off never changes uploaded copies`() {
        assertEquals(SyncState.SYNCED, SyncPlanner.onSyncToggled(false, SyncState.SYNCED))
        assertEquals(SyncState.LOCAL, SyncPlanner.onSyncToggled(false, SyncState.LOCAL))
    }

    @Test
    fun `upload results map to states`() {
        assertEquals(SyncState.SYNCED, SyncPlanner.afterUpload())
        assertEquals(SyncState.FAILED, SyncPlanner.afterFailure())
    }
}

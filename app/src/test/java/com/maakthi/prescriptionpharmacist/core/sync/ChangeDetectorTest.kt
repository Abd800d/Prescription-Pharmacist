package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.ChangeKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Local/remote change detection — both-changed is ALWAYS a conflict. */
class ChangeDetectorTest {

    @Test
    fun `new local record is pushed`() {
        assertEquals(ChangeKind.LOCAL_CHANGED, ChangeDetector.detect(100L, null, lastSyncAt = 50L))
    }

    @Test
    fun `new remote record is pulled`() {
        assertEquals(ChangeKind.REMOTE_CHANGED, ChangeDetector.detect(null, 100L, lastSyncAt = 50L))
    }

    @Test
    fun `nothing changed`() {
        assertEquals(ChangeKind.NONE, ChangeDetector.detect(30L, 30L, lastSyncAt = 50L))
        assertEquals(ChangeKind.NONE, ChangeDetector.detect(null, null, lastSyncAt = 50L))
    }

    @Test
    fun `only local changed is pushed`() {
        assertEquals(ChangeKind.LOCAL_CHANGED, ChangeDetector.detect(100L, 30L, lastSyncAt = 50L))
    }

    @Test
    fun `only remote changed is pulled`() {
        assertEquals(ChangeKind.REMOTE_CHANGED, ChangeDetector.detect(30L, 100L, lastSyncAt = 50L))
    }

    @Test
    fun `both changed is always a conflict`() {
        assertEquals(ChangeKind.BOTH_CHANGED, ChangeDetector.detect(100L, 100L, lastSyncAt = 50L))
        assertEquals(ChangeKind.BOTH_CHANGED, ChangeDetector.detect(60L, 90L, lastSyncAt = 50L))
    }
}

package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Offline-first sync engine: push/pull, conflicts surfaced (never silent
 * overwrite), and — critically — internet interruption mid-sync leaves no
 * data loss and resumes cleanly.
 */
class SyncEngineTest {

    private class FakeGateway(
        var failPush: Boolean = false,
        var failPull: Boolean = false
    ) : SyncGateway {
        val pushed = mutableListOf<SyncRecord>()
        var remote = mutableListOf<SyncRecord>()

        override suspend fun push(type: SyncEntityType, records: List<SyncRecord>) {
            if (failPush) throw IOException("connection lost")
            pushed += records
        }

        override suspend fun pull(type: SyncEntityType, since: Long): List<SyncRecord> {
            if (failPull) throw IOException("connection lost")
            return remote.filter { it.updatedAt > since }
        }
    }

    private class FakeStore : SyncLocalStore {
        var lastSync = 0L
        val local = mutableMapOf<String, SyncRecord>()
        val applied = mutableListOf<SyncRecord>()
        val conflicts = mutableListOf<Pair<SyncRecord, SyncRecord>>()
        val resolutions = mutableListOf<SyncRecord>()

        override suspend fun lastSyncAt(type: SyncEntityType): Long = lastSync
        override suspend fun setLastSyncAt(type: SyncEntityType, at: Long) { lastSync = at }
        override suspend fun localRecord(type: SyncEntityType, id: String): SyncRecord? = local[id]
        override suspend fun dirtyRecords(type: SyncEntityType, since: Long): List<SyncRecord> =
            local.values.filter { it.updatedAt > since }
        override suspend fun applyRemote(type: SyncEntityType, record: SyncRecord) {
            applied += record
            local[record.id] = record
        }
        override suspend fun recordConflict(type: SyncEntityType, id: String, local: SyncRecord, remote: SyncRecord) {
            conflicts += local to remote
        }
        override suspend fun applyResolution(type: SyncEntityType, id: String, winning: SyncRecord) {
            resolutions += winning
            local[id] = winning
        }
    }

    // ---------- happy path ----------

    @Test
    fun `pushes local changes and pulls remote changes`() = runTest {
        val gateway = FakeGateway()
        val store = FakeStore()
        store.local["d1"] = SyncRecord("d1", updatedAt = 100)
        gateway.remote += SyncRecord("d2", updatedAt = 120)

        val report = SyncEngine(gateway, store).syncType(SyncEntityType.DRUG, now = 200)

        assertTrue(report.success)
        assertEquals(1, report.pushed)
        assertEquals(1, report.pulled)
        assertEquals(200L, store.lastSync)
        assertEquals("d2", store.applied.single().id)
    }

    // ---------- internet interruption ----------

    @Test
    fun `internet loss mid-sync interrupts safely with no data loss`() = runTest {
        val gateway = FakeGateway()
        val store = FakeStore()
        val engine = SyncEngine(gateway, store)
        store.local["d1"] = SyncRecord("d1", updatedAt = 100)
        val first = engine.syncType(SyncEntityType.DRUG, now = 200)
        assertTrue(first.success) // first pass fine

        // second run: local change pushes, then the connection drops on pull
        gateway.failPull = true
        store.local["d2"] = SyncRecord("d2", updatedAt = 300)
        val interrupted = engine.syncType(SyncEntityType.DRUG, now = 400)

        assertTrue(interrupted.interrupted)
        assertEquals(0, interrupted.pulled)
        // clock did NOT advance → the next run retries everything safely
        assertEquals(200L, store.lastSync)
        // local data untouched
        assertEquals(2, store.local.size)
    }

    @Test
    fun `sync resumes completely after the connection returns`() = runTest {
        val gateway = FakeGateway()
        val store = FakeStore()
        val engine = SyncEngine(gateway, store)

        store.local["d1"] = SyncRecord("d1", updatedAt = 100)
        gateway.remote += SyncRecord("d3", updatedAt = 150)
        engine.syncType(SyncEntityType.DRUG, now = 200) // ok

        gateway.failPull = true
        store.local["d2"] = SyncRecord("d2", updatedAt = 300)
        val interrupted = engine.syncType(SyncEntityType.DRUG, now = 400)
        assertTrue(interrupted.interrupted)

        // connection returns
        gateway.failPull = false
        val resumed = engine.syncType(SyncEntityType.DRUG, now = 500)
        assertTrue(resumed.success)
        assertEquals(1, resumed.pushed) // d2 went up
        assertEquals(300, gateway.pushed.first { it.id == "d2" }.updatedAt)
        assertEquals(500L, store.lastSync) // clock advanced on the full pass
    }

    @Test
    fun `push failure marks the run interrupted and keeps local records`() = runTest {
        val gateway = FakeGateway(failPush = true) // every push dies
        val store = FakeStore()
        store.local["d1"] = SyncRecord("d1", updatedAt = 100)

        val report = SyncEngine(gateway, store).syncType(SyncEntityType.DRUG, now = 200)

        assertTrue(report.interrupted)
        assertEquals(0, report.pushed)
        assertEquals(0L, store.lastSync)
        assertEquals(1, store.local.size) // nothing lost
    }

    // ---------- conflicts ----------

    @Test
    fun `both-changed records surface as conflicts and are never overwritten`() = runTest {
        val gateway = FakeGateway()
        val store = FakeStore()
        store.lastSync = 50
        store.local["d1"] = SyncRecord("d1", updatedAt = 100, payloadJson = "my edit")
        gateway.remote += SyncRecord("d1", updatedAt = 120, payloadJson = "their edit")

        val report = SyncEngine(gateway, store).syncType(SyncEntityType.DRUG, now = 200)

        assertEquals(1, report.conflicts)
        assertTrue(store.conflicts.isNotEmpty())
        // local version NOT replaced
        assertEquals("my edit", store.local["d1"]?.payloadJson)
        assertTrue(store.applied.none { it.id == "d1" })
    }

    // ---------- offline-first guarantee ----------

    @Test
    fun `sync never deletes local records`() = runTest {
        val gateway = FakeGateway()
        val store = FakeStore()
        store.local["keep"] = SyncRecord("keep", updatedAt = 10)
        gateway.remote += SyncRecord("remote", updatedAt = 120)

        SyncEngine(gateway, store).syncType(SyncEntityType.DRUG, now = 200)

        assertTrue(store.local.containsKey("keep"))
        assertTrue(store.local.containsKey("remote"))
    }
}

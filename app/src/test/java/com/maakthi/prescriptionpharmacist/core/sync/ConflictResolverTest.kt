package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conflicts resolve ONLY by explicit user choice — never automatically. */
class ConflictResolverTest {

    private val conflict = SyncConflict(
        id = "c1",
        entityType = SyncEntityType.DRUG,
        entityId = "d1",
        label = "Panadol",
        localUpdatedAt = 100L,
        remoteUpdatedAt = 200L,
        localJson = """{"name":"my edit"}""",
        remoteJson = """{"name":"their edit"}""",
        detectedAt = 300L
    )

    @Test
    fun `keep local preserves the user edit`() {
        val resolution = ConflictResolver.resolve(conflict, ConflictChoice.KEEP_LOCAL, now = 400L)
        assertEquals("""{"name":"my edit"}""", resolution.winningJson)
        assertEquals(400L, resolution.winningUpdatedAt)
    }

    @Test
    fun `keep remote applies the server version`() {
        val resolution = ConflictResolver.resolve(conflict, ConflictChoice.KEEP_REMOTE, now = 400L)
        assertEquals("""{"name":"their edit"}""", resolution.winningJson)
        assertEquals(400L, resolution.winningUpdatedAt)
    }

    @Test
    fun `both versions are preserved until the user chooses`() {
        assertTrue(ConflictResolver.preservesBothVersions(conflict))
    }
}

package com.maakthi.prescriptionpharmacist.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upload guard: executables are rejected by name AND magic bytes, only real
 * image payloads pass, and every file is size-capped.
 */
class UploadGuardTest {

    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0)
    private fun png() = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0
    )
    private fun elf() = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 0)
    private fun mz() = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0)
    private fun pk() = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)
    private fun shebang() = byteArrayOf('#'.code.toByte(), '!'.code.toByte(), '/'.code.toByte(), 'b'.code.toByte())

    @Test
    fun `real jpeg and png pass`() {
        assertEquals(
            UploadGuard.Decision.Allowed,
            UploadGuard.checkImage("photo.jpg", "image/jpeg", jpeg())
        )
        assertEquals(
            UploadGuard.Decision.Allowed,
            UploadGuard.checkImage("shot.PNG", "image/png", png())
        )
    }

    @Test
    fun `executables are rejected by extension`() {
        val result = UploadGuard.checkImage("virus.exe", "image/jpeg", jpeg())
        assertTrue(result is UploadGuard.Decision.Rejected)
        assertEquals(
            UploadGuard.Reason.EXECUTABLE_CONTENT,
            (result as UploadGuard.Decision.Rejected).reason
        )
        assertTrue(UploadGuard.isExecutableName("app.apk"))
        assertTrue(UploadGuard.isExecutableName("run.sh"))
        assertTrue(UploadGuard.isExecutableName("lib.so"))
    }

    @Test
    fun `executables disguised as images are rejected by magic bytes`() {
        for (payload in listOf(elf(), mz(), pk(), shebang())) {
            val result = UploadGuard.checkImage("cute.jpg", "image/jpeg", payload)
            assertTrue(
                "payload must be rejected",
                result is UploadGuard.Decision.Rejected &&
                    result.reason == UploadGuard.Reason.EXECUTABLE_CONTENT
            )
        }
        // an APK renamed to .png is still a zip (PK) → rejected
        assertTrue(UploadGuard.looksExecutable(pk()))
        assertFalse(UploadGuard.looksExecutable(jpeg()))
    }

    @Test
    fun `non-image types are rejected`() {
        val result = UploadGuard.checkImage("doc.pdf", "application/pdf", jpeg())
        assertTrue(result is UploadGuard.Decision.Rejected)
        // correct extension but not really an image
        val fake = UploadGuard.checkImage("x.jpg", "image/jpeg", "just text".toByteArray())
        assertEquals(
            UploadGuard.Reason.BAD_TYPE,
            (fake as UploadGuard.Decision.Rejected).reason
        )
    }

    @Test
    fun `oversized and empty files are rejected`() {
        val big = ByteArray(UploadGuard.MAX_IMAGE_BYTES + 1) { 0xFF.toByte() }
        assertEquals(
            UploadGuard.Reason.TOO_LARGE,
            (UploadGuard.checkImage("big.jpg", "image/jpeg", big) as UploadGuard.Decision.Rejected).reason
        )
        assertEquals(
            UploadGuard.Reason.EMPTY,
            (UploadGuard.checkImage("empty.jpg", "image/jpeg", ByteArray(0)) as UploadGuard.Decision.Rejected).reason
        )
    }

    @Test
    fun `backups must be ppbak and size-capped`() {
        assertEquals(
            UploadGuard.Decision.Allowed,
            UploadGuard.checkBackup("backup.ppbak", ByteArray(100) { 1 })
        )
        assertEquals(
            UploadGuard.Reason.BAD_TYPE,
            (UploadGuard.checkBackup("backup.zip", ByteArray(100)) as UploadGuard.Decision.Rejected).reason
        )
        val big = ByteArray(UploadGuard.MAX_BACKUP_BYTES + 1)
        assertEquals(
            UploadGuard.Reason.TOO_LARGE,
            (UploadGuard.checkBackup("backup.ppbak", big) as UploadGuard.Decision.Rejected).reason
        )
    }

    // ---------- audit sanitizer: no health data in logs ----------

    @Test
    fun `audit lines carry only ids and codes — never free text`() {
        val line = AuditSanitizer.toLogLine(
            AuditEvent.Sync(action = "pull", subjectId = "abc-123", count = 4),
            atMillis = 1_700_000_000_000L
        )
        assertEquals("audit t=sync a=pull id=abc-123 at=1700000000000 count=4", line)
    }

    @Test
    fun `free text and health data collapse to safe tokens`() {
        val line = AuditSanitizer.toLogLine(
            AuditEvent.Support(action = "report: panadol 500mg twice daily", subjectId = "patient name"),
            atMillis = 1L
        )
        assertFalse(line.contains("panadol"))
        assertFalse(line.contains("patient"))
        assertTrue(line.contains("a=other"))
        assertTrue(line.contains("id=redacted"))
    }

    @Test
    fun `sanitize token and id rules`() {
        assertEquals("sync.run", AuditSanitizer.sanitizeToken("sync.run"))
        assertEquals("other", AuditSanitizer.sanitizeToken("SYNC RUN <script>"))
        assertEquals("SUP-123-ABC", AuditSanitizer.sanitizeId("SUP-123-ABC"))
        assertEquals("redacted", AuditSanitizer.sanitizeId("Ahmed Ali, metformin"))
    }

    // ---------- deletion policy coverage ----------

    @Test
    fun `local wipe covers every local category`() {
        assertTrue(
            DataDeletionPolicy.localWipeCoversAllLocalData(DataDeletionPolicy.LOCAL_CATEGORIES)
        )
        assertFalse(
            DataDeletionPolicy.localWipeCoversAllLocalData(listOf("local.database"))
        )
        assertTrue(DataDeletionPolicy.LOCAL_CATEGORIES.contains("local.media"))
        assertTrue(DataDeletionPolicy.CLOUD_CATEGORIES.contains("cloud.account"))
    }
}

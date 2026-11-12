package com.maakthi.prescriptionpharmacist.core.security

/**
 * Upload guard for every file the app writes or sends to the cloud.
 *
 * Product law: only image payloads are accepted — executable content is
 * rejected by extension AND magic bytes, and every file is size-capped.
 * Works on raw bytes (before any compression) so nothing untrusted slips in.
 */
object UploadGuard {

    /** Hard cap for any image (raw input, pre-compression). */
    const val MAX_IMAGE_BYTES: Int = 20 * 1024 * 1024

    /** Hard cap for encrypted backup files. */
    const val MAX_BACKUP_BYTES: Int = 25 * 1024 * 1024

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")

    /** Extensions that must NEVER be accepted regardless of content. */
    val EXECUTABLE_EXTENSIONS = setOf(
        "exe", "apk", "dex", "jar", "so", "dll", "bat", "cmd", "sh", "ps1",
        "js", "vbs", "php", "html", "htm", "svg", "msi", "scr", "com", "pif"
    )

    /** Magic signatures of executable/archive payloads. */
    private val EXECUTABLE_MAGIC = listOf(
        byteArrayOf(0x4D, 0x5A),                   // MZ  (Windows PE)
        byteArrayOf(0x7F, 0x45, 0x4C, 0x46),       // ELF (Linux)
        byteArrayOf(0x23, 0x21),                   // #! (scripts)
        byteArrayOf(0x64, 0x65, 0x78, 0x0A),       // dex (Android)
        byteArrayOf(0x50, 0x4B, 0x03, 0x04),       // PK  (zip/apk/jar)
        byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    )

    sealed interface Decision {
        data object Allowed : Decision
        data class Rejected(val reason: Reason) : Decision
    }

    enum class Reason { EXECUTABLE_CONTENT, BAD_TYPE, TOO_LARGE, EMPTY }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    fun isExecutableName(fileName: String): Boolean =
        extensionOf(fileName) in EXECUTABLE_EXTENSIONS

    fun looksExecutable(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return EXECUTABLE_MAGIC.any { magic ->
            bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }
        }
    }

    fun looksLikeImage(bytes: ByteArray): Boolean =
        isJpeg(bytes) || isPng(bytes) || isWebp(bytes) || isGif(bytes)

    private fun isJpeg(b: ByteArray) =
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPng(b: ByteArray) =
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() &&
            b[3] == 0x47.toByte() && b[4] == 0x0D.toByte() && b[5] == 0x0A.toByte() &&
            b[6] == 0x1A.toByte() && b[7] == 0x0A.toByte()

    private fun isWebp(b: ByteArray) =
        b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()

    private fun isGif(b: ByteArray) =
        b.size >= 4 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == '8'.code.toByte()

    /**
     * Bytes-only validation for content that has no meaningful name (camera
     * / gallery payloads): not empty, size-capped, not executable, real image.
     */
    fun checkImageBytes(bytes: ByteArray): Decision {
        if (bytes.isEmpty()) return Decision.Rejected(Reason.EMPTY)
        if (bytes.size > MAX_IMAGE_BYTES) return Decision.Rejected(Reason.TOO_LARGE)
        if (looksExecutable(bytes)) return Decision.Rejected(Reason.EXECUTABLE_CONTENT)
        if (!looksLikeImage(bytes)) return Decision.Rejected(Reason.BAD_TYPE)
        return Decision.Allowed
    }

    /**
     * Validates an image upload: not empty, not executable (name or magic),
     * actually an image, allowed type, within the size cap.
     */
    fun checkImage(fileName: String, mimeType: String, bytes: ByteArray): Decision {
        if (isExecutableName(fileName)) return Decision.Rejected(Reason.EXECUTABLE_CONTENT)
        if (extensionOf(fileName) !in IMAGE_EXTENSIONS || mimeType.lowercase() !in IMAGE_MIMES) {
            return Decision.Rejected(Reason.BAD_TYPE)
        }
        return checkImageBytes(bytes)
    }

    /** Validates an encrypted backup upload (.ppbak only, size-capped). */
    fun checkBackup(fileName: String, bytes: ByteArray): Decision {
        if (bytes.isEmpty()) return Decision.Rejected(Reason.EMPTY)
        if (bytes.size > MAX_BACKUP_BYTES) return Decision.Rejected(Reason.TOO_LARGE)
        if (extensionOf(fileName) != "ppbak") return Decision.Rejected(Reason.BAD_TYPE)
        return Decision.Allowed
    }
}

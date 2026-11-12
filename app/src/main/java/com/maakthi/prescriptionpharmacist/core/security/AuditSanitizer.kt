package com.maakthi.prescriptionpharmacist.core.security

/**
 * Audit events for security-relevant actions. Every event is reduced to a
 * single safe line by [AuditSanitizer]: IDs and codes only — NEVER medication
 * names, diagnoses, prescription text or other health data (product law).
 */
sealed interface AuditEvent {
    val action: String
    val subjectId: String

    data class Auth(override val action: String, override val subjectId: String) : AuditEvent
    data class Sync(override val action: String, override val subjectId: String, val count: Int = 0) : AuditEvent
    data class Backup(override val action: String, override val subjectId: String) : AuditEvent
    data class Media(override val action: String, override val subjectId: String) : AuditEvent
    data class Support(override val action: String, override val subjectId: String) : AuditEvent
    data class Privacy(override val action: String, override val subjectId: String) : AuditEvent
}

/**
 * Turns audit events into log lines that are safe for logcat and crash
 * reports: fixed action codes + opaque IDs only, free text stripped.
 */
object AuditSanitizer {

    private val SAFE_ID = Regex("^[A-Za-z0-9_\\-:.]{1,64}$")

    fun toLogLine(event: AuditEvent, atMillis: Long): String {
        val type = when (event) {
            is AuditEvent.Auth -> "auth"
            is AuditEvent.Sync -> "sync"
            is AuditEvent.Backup -> "backup"
            is AuditEvent.Media -> "media"
            is AuditEvent.Support -> "support"
            is AuditEvent.Privacy -> "privacy"
        }
        val action = sanitizeToken(event.action)
        val subject = sanitizeId(event.subjectId)
        val extra = (event as? AuditEvent.Sync)?.count?.let { " count=$it" } ?: ""
        return "audit t=$type a=$action id=$subject at=$atMillis$extra"
    }

    /** Fixed-format tokens only; anything unusual collapses to "other". */
    fun sanitizeToken(value: String): String =
        if (value.matches(Regex("^[a-z][a-z0-9_.]{0,31}$"))) value else "other"

    /** Opaque IDs only; anything else becomes "redacted" (never free text). */
    fun sanitizeId(value: String): String =
        if (SAFE_ID.matches(value)) value else "redacted"
}

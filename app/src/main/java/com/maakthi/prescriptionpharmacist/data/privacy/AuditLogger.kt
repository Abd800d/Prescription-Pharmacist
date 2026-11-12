package com.maakthi.prescriptionpharmacist.data.privacy

import android.util.Log
import com.maakthi.prescriptionpharmacist.core.security.AuditEvent
import com.maakthi.prescriptionpharmacist.core.security.AuditSanitizer
import com.maakthi.prescriptionpharmacist.data.local.dao.AuditEventDao
import com.maakthi.prescriptionpharmacist.data.local.entity.AuditEventEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes security-relevant events to the local audit log. Every line goes
 * through [AuditSanitizer] first: IDs and codes only — health information can
 * never leak into logs or crash reports.
 */
@Singleton
class AuditLogger @Inject constructor(
    private val dao: AuditEventDao
) {
    suspend fun log(event: AuditEvent) {
        val at = System.currentTimeMillis()
        val line = AuditSanitizer.toLogLine(event, at)
        // logcat gets the sanitized line only
        Log.i(TAG, line)
        dao.insert(
            AuditEventEntity(
                id = UUID.randomUUID().toString(),
                type = line.substringAfter("t=").substringBefore(" "),
                action = AuditSanitizer.sanitizeToken(event.action),
                subjectId = AuditSanitizer.sanitizeId(event.subjectId),
                at = at,
                count = (event as? AuditEvent.Sync)?.count ?: 0
            )
        )
    }

    companion object {
        private const val TAG = "PPAudit"
    }
}

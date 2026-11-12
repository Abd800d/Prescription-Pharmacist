package com.maakthi.prescriptionpharmacist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local audit-log entry (security-relevant events). Holds ONLY opaque IDs
 * and fixed action codes — never medication names or health data.
 */
@Entity(tableName = "audit_log")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val action: String,
    val subjectId: String,
    val at: Long,
    val count: Int
)

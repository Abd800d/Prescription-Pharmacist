package com.maakthi.prescriptionpharmacist.domain.model

/**
 * A problem report sent to technical support. Personal data (contact email)
 * is included ONLY with explicit user consent — see
 * com.maakthi.prescriptionpharmacist.core.support.SupportReportBuilder.
 */
data class SupportReport(
    val reportId: String,
    val appVersion: String,
    val appVersionCode: Int,
    val message: String,
    val contactEmail: String?,
    val contactConsent: Boolean,
    val includeLog: Boolean,
    val includeScreenshot: Boolean,
    val createdAt: Long
)

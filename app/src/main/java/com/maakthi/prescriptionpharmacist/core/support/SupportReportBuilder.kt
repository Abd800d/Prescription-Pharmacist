package com.maakthi.prescriptionpharmacist.core.support

import com.maakthi.prescriptionpharmacist.domain.model.SupportReport
import java.net.URLEncoder

/**
 * Pure logic behind the support page: report identity, the consent rule for
 * personal data, and the report/contact link composition.
 *
 * Product law: NO personal data leaves the device without explicit consent,
 * every report carries its own ID and the app version, and support never
 * provides medical diagnosis.
 */
object SupportReportBuilder {

    /** WhatsApp support line in full international format (Yemen +967). */
    const val WHATSAPP_NUMBER = "967781733795"

    /** Support inbox. */
    const val SUPPORT_EMAIL = "7a8b1d@gmail.com"

    /** Shown inside every sent report — support is not a medical service. */
    const val NO_MEDICAL_NOTICE_EN =
        "IMPORTANT: Technical support does NOT provide medical diagnosis."

    const val NO_MEDICAL_NOTICE_AR =
        "تنبيه: الدعم الفني لا يقدم تشخيصاً طبياً — راجع طبيباً أو صيدلانياً."

    /**
     * Unique, human-readable report ID: SUP-<base36 time>-<base36 nonce>.
     * Deterministic for a given (now, nonce) pair so it is testable.
     */
    fun reportId(nowMillis: Long, nonce: Long): String {
        val time = nowMillis.toString(36).uppercase()
        val random = (nonce and 0xFFFFFFFFL).toString(36).uppercase()
        return "SUP-$time-$random"
    }

    fun isMessageValid(message: String): Boolean = message.trim().isNotEmpty()

    /**
     * Builds the report. The contact email is dropped entirely unless the
     * user explicitly consented — personal data is never sent silently.
     */
    fun build(
        reportId: String,
        appVersion: String,
        appVersionCode: Int,
        message: String,
        contactEmail: String?,
        contactConsent: Boolean,
        includeLog: Boolean,
        includeScreenshot: Boolean,
        createdAt: Long
    ): SupportReport = SupportReport(
        reportId = reportId,
        appVersion = appVersion,
        appVersionCode = appVersionCode,
        message = message.trim(),
        contactEmail = contactEmail?.trim()
            ?.takeIf { it.isNotEmpty() && contactConsent },
        contactConsent = contactConsent,
        includeLog = includeLog,
        includeScreenshot = includeScreenshot,
        createdAt = createdAt
    )

    fun emailSubject(report: SupportReport): String =
        "Prescription Pharmacist — problem report ${report.reportId}"

    fun emailBody(report: SupportReport): String = buildString {
        appendLine("Report ID: ${report.reportId}")
        appendLine("App version: ${report.appVersion} (${report.appVersionCode})")
        appendLine("Reported at: ${report.createdAt}")
        appendLine()
        appendLine("Problem description:")
        appendLine(report.message)
        appendLine()
        if (report.contactEmail != null) {
            appendLine("Contact for reply: ${report.contactEmail}")
        } else {
            appendLine("Contact for reply: (not provided)")
        }
        appendLine("Error log attached: ${if (report.includeLog) "yes" else "no"}")
        appendLine("Screenshot attached: ${if (report.includeScreenshot) "yes" else "no"}")
        appendLine()
        appendLine("---")
        appendLine(NO_MEDICAL_NOTICE_EN)
        appendLine(NO_MEDICAL_NOTICE_AR)
    }

    /** WhatsApp deep link (opens the chat with the support number). */
    fun whatsappLink(): String = "https://wa.me/$WHATSAPP_NUMBER"

    /** mailto: link for the quick "send email" button. */
    fun mailtoLink(subject: String): String =
        "mailto:$SUPPORT_EMAIL?subject=" + URLEncoder.encode(subject, "UTF-8")
}

package com.maakthi.prescriptionpharmacist.core.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support report rules: unique report IDs, app version always included,
 * and — critically — NO personal data is ever sent without explicit consent.
 */
class SupportReportBuilderTest {

    private fun build(
        contactEmail: String? = null,
        contactConsent: Boolean = false,
        message: String = "  The app crashes when I open history.  "
    ) = SupportReportBuilder.build(
        reportId = "SUP-TEST-1",
        appVersion = "1.0.0",
        appVersionCode = 1,
        message = message,
        contactEmail = contactEmail,
        contactConsent = contactConsent,
        includeLog = true,
        includeScreenshot = false,
        createdAt = 1_700_000_000_000L
    )

    // ---------- report ID ----------

    @Test
    fun `report id has the SUP format and is unique per nonce`() {
        val id1 = SupportReportBuilder.reportId(nowMillis = 1_700_000_000_000L, nonce = 42L)
        val id2 = SupportReportBuilder.reportId(nowMillis = 1_700_000_000_000L, nonce = 43L)
        assertTrue(id1.matches(Regex("SUP-[A-Z0-9]+-[A-Z0-9]+")))
        assertNotEquals(id1, id2)
    }

    // ---------- consent rule (product law) ----------

    @Test
    fun `contact email is never included without consent`() {
        val report = build(contactEmail = "user@example.com", contactConsent = false)
        assertNull(report.contactEmail)
    }

    @Test
    fun `contact email is included only with explicit consent`() {
        val report = build(contactEmail = "user@example.com", contactConsent = true)
        assertEquals("user@example.com", report.contactEmail)
    }

    @Test
    fun `blank contact email is dropped even with consent`() {
        val report = build(contactEmail = "   ", contactConsent = true)
        assertNull(report.contactEmail)
    }

    // ---------- report content ----------

    @Test
    fun `message is trimmed and required`() {
        val report = build(message = "  crash on open  ")
        assertEquals("crash on open", report.message)
        assertTrue(SupportReportBuilder.isMessageValid("x"))
        assertFalse(SupportReportBuilder.isMessageValid("   "))
    }

    @Test
    fun `email body always carries report id and app version`() {
        val body = SupportReportBuilder.emailBody(build())
        assertTrue(body.contains("SUP-TEST-1"))
        assertTrue(body.contains("1.0.0 (1)"))
        assertTrue(body.contains("The app crashes"))
    }

    @Test
    fun `email body carries the no-medical-diagnosis notice in both languages`() {
        val body = SupportReportBuilder.emailBody(build())
        assertTrue(body.contains(SupportReportBuilder.NO_MEDICAL_NOTICE_EN))
        assertTrue(body.contains(SupportReportBuilder.NO_MEDICAL_NOTICE_AR))
    }

    @Test
    fun `email body never leaks the contact without consent`() {
        val withConsent = SupportReportBuilder.emailBody(
            build(contactEmail = "user@example.com", contactConsent = true)
        )
        val without = SupportReportBuilder.emailBody(
            build(contactEmail = "user@example.com", contactConsent = false)
        )
        assertTrue(withConsent.contains("user@example.com"))
        assertFalse(without.contains("user@example.com"))
    }

    @Test
    fun `subject carries the report id`() {
        val report = build()
        assertTrue(SupportReportBuilder.emailSubject(report).contains("SUP-TEST-1"))
    }

    // ---------- contact links ----------

    @Test
    fun `whatsapp link uses the international support number`() {
        assertEquals("https://wa.me/967781733795", SupportReportBuilder.whatsappLink())
    }

    @Test
    fun `mailto link targets the support inbox with encoded subject`() {
        val link = SupportReportBuilder.mailtoLink("problem report SUP-TEST-1")
        assertTrue(link.startsWith("mailto:7a8b1d@gmail.com?subject="))
        assertTrue(link.contains("problem+report"))
    }
}

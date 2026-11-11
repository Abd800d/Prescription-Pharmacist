package com.maakthi.prescriptionpharmacist.core.assistant

import com.maakthi.prescriptionpharmacist.domain.model.EvidenceItem
import com.maakthi.prescriptionpharmacist.domain.model.EvidencePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answer guardrails: refusal, citation whitelist, referral, disclaimer. */
class AnswerGuardTest {

    private val evidence = listOf(
        EvidenceItem(1, "Panadol — Indications: pain", "openFDA", "https://fda.example/1", "2026-01-01"),
        EvidenceItem(2, "Panadol — Warnings: liver", "DailyMed", "https://dailymed.example/2", "2026-02-02")
    )
    private val pack = EvidencePack(items = evidence)

    // ---------- language & canned answers ----------

    @Test
    fun `language detection works both ways`() {
        assertEquals("ar", AnswerGuard.languageOf("ما هي جرعة البنادول؟"))
        assertEquals("en", AnswerGuard.languageOf("What is the dose of Panadol?"))
    }

    @Test
    fun `no evidence means the explicit refusal`() {
        val answer = AnswerGuard.finalize("ما هو الدواء؟", "", EvidencePack(emptyList()))
        assertTrue(answer.insufficient)
        assertEquals(AnswerGuard.REFUSAL_AR, answer.reply)
        assertTrue(answer.citations.isEmpty())
    }

    // ---------- fabrication prevention ----------

    @Test
    fun `foreign urls are stripped from the reply`() {
        val dirty = "Take care [1]. See https://fake-source.example/claim and also http://spam.test/x"
        val clean = AnswerGuard.sanitizeReply(dirty, setOf("https://fda.example/1"))
        assertFalse(clean.contains("fake-source"))
        assertFalse(clean.contains("spam.test"))
        assertTrue(clean.contains("Take care [1]"))
    }

    @Test
    fun `markdown links to fabricated sources keep only their label`() {
        val dirty = "According to [WHO report](https://who-fake.example/pdf) the drug is safe [2]."
        val clean = AnswerGuard.sanitizeReply(dirty, setOf("https://dailymed.example/2"))
        assertTrue(clean.contains("WHO report"))
        assertFalse(clean.contains("who-fake.example"))
    }

    @Test
    fun `links to real evidence are kept`() {
        val dirty = "Source: https://fda.example/1 confirms [1]."
        val clean = AnswerGuard.sanitizeReply(dirty, setOf("https://fda.example/1"))
        assertTrue(clean.contains("https://fda.example/1"))
    }

    @Test
    fun `citations come only from evidence`() {
        val reply = "Pain relief [1] and liver warning [2] and mystery [9]."
        val citations = AnswerGuard.buildCitations(reply, evidence)
        assertEquals(2, citations.size)
        assertEquals("openFDA", citations[0].sourceName)
        assertEquals("2026-01-01", citations[0].sourceDate)
    }

    @Test
    fun `uncited reply still exposes its real evidence sources`() {
        val citations = AnswerGuard.buildCitations("Pain relief.", evidence)
        assertEquals(2, citations.size)
        assertTrue(citations.none { it.sourceUrl.contains("fake") })
    }

    // ---------- safety notices ----------

    @Test
    fun `stopping a medication triggers the referral notice`() {
        assertTrue(AnswerGuard.needsReferral("هل أوقف الميتفورمين الآن؟"))
        assertTrue(AnswerGuard.needsReferral("Can I stop taking Metformin?"))
        assertTrue(AnswerGuard.needsReferral("غيّر لي جرعة الانسولين"))
        assertTrue(AnswerGuard.needsReferral("increase the dose of warfarin"))
    }

    @Test
    fun `diagnosis questions trigger the referral notice`() {
        assertTrue(AnswerGuard.needsReferral("ما هو التشخيص؟"))
        assertTrue(AnswerGuard.needsReferral("diagnose my condition"))
    }

    @Test
    fun `ordinary drug questions do not trigger the referral notice`() {
        assertFalse(AnswerGuard.needsReferral("ما هي آثار البنادول الجانبية؟"))
        assertFalse(AnswerGuard.needsReferral("What are the side effects of Panadol?"))
    }

    @Test
    fun `finalized answer always carries the permanent disclaimer`() {
        val answer = AnswerGuard.finalize("Tell me about Panadol", "It relieves pain [1].", pack)
        assertTrue(answer.reply.contains(AnswerGuard.DISCLAIMER_EN))
        assertFalse(answer.insufficient)
    }

    @Test
    fun `arabic answer gets arabic disclaimer and referral`() {
        val answer = AnswerGuard.finalize("هل أوقف البنادول؟", "نعم اوقفه [1].", pack)
        assertTrue(answer.reply.contains(AnswerGuard.REFERRAL_AR))
        assertTrue(answer.reply.contains(AnswerGuard.DISCLAIMER_AR))
    }

    @Test
    fun `fabricated links never survive into the final answer`() {
        val answer = AnswerGuard.finalize(
            "Tell me about Panadol",
            "See [this study](https://fake.example/study) and https://spam.example [1].",
            pack
        )
        assertFalse(answer.reply.contains("fake.example"))
        assertFalse(answer.reply.contains("spam.example"))
        assertTrue(answer.citations.all { it.sourceUrl in setOf("https://fda.example/1", "https://dailymed.example/2", "") })
    }
}

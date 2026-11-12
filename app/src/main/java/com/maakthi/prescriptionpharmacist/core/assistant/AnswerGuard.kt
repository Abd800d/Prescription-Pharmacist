package com.maakthi.prescriptionpharmacist.core.assistant

import com.maakthi.prescriptionpharmacist.domain.model.AssistantAnswer
import com.maakthi.prescriptionpharmacist.domain.model.AssistantCitation
import com.maakthi.prescriptionpharmacist.domain.model.EvidenceItem
import com.maakthi.prescriptionpharmacist.domain.model.EvidencePack

/**
 * Answer guardrails — everything the model says is checked here:
 *
 * - answers with no reliable evidence get the explicit refusal;
 * - citations may ONLY point at real evidence items (fabricated sources and
 *   foreign links are stripped from the reply);
 * - diagnosis requests and dose-change/stop questions always get a
 *   referral-to-physician notice;
 * - every answer carries the permanent disclaimer.
 */
object AnswerGuard {

    const val REFUSAL_AR = "لا توجد معلومات موثوقة كافية للإجابة عن هذا السؤال. يرجى الرجوع إلى الطبيب أو الصيدلي."
    const val REFUSAL_EN = "There is not enough reliable information to answer this question. Please consult a doctor or pharmacist."

    const val DISCLAIMER_AR = "⚠️ هذا المساعد لا يغني عن الطبيب أو الصيدلي — المعلومات إرشادية فقط."
    const val DISCLAIMER_EN = "⚠️ This assistant does not replace a doctor or pharmacist — information is guidance only."

    const val REFERRAL_AR = "⚠️ لا يقدّم المساعد تشخيصاً نهائياً ولا يغيّر الجرعة أو يوقف الدواء — راجع طبيبك قبل أي تعديل."
    const val REFERRAL_EN = "⚠️ The assistant does not provide a final diagnosis and never changes or stops a dose — consult your doctor before any change."

    private val URL_REGEX = Regex("""https?://[^\s)\]>"'`]+""")
    private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
    private val CITED_ID_REGEX = Regex("""\[(\d{1,2})]""")

    /** Detects the question language from its Arabic content. */
    fun languageOf(text: String): String {
        val arabic = text.count { it in '\u0600'..'\u06FF' }
        return if (arabic >= 2 || arabic * 3 >= text.length) "ar" else "en"
    }

    fun refusal(language: String): String = if (language == "ar") REFUSAL_AR else REFUSAL_EN

    fun disclaimer(language: String): String = if (language == "ar") DISCLAIMER_AR else DISCLAIMER_EN

    fun referral(language: String): String = if (language == "ar") REFERRAL_AR else REFERRAL_EN

    /** True when the question asks for a final diagnosis or a dose change/stop. */
    fun needsReferral(question: String): Boolean {
        val patterns = listOf(
            // final diagnosis
            "التشخيص", "شخّصني", "شخصني", "ما مرضي", "ما هو مرضي",
            "diagnose", "diagnosis", "what disease", "what illness",
            // stop medication
            "أوقف الدواء", "اقطع الدواء", "اتوقف عن الدواء", "توقف عن الدواء",
            "هل اوقف", "هل أوقف", "اوقف الدواء",
            "stop the medication", "stop taking", "quit the medication", "discontinue"
        )
        val doseChange = Regex(
            """غي.?ر.{0,20}جرعه|ازيد.{0,20}جرعه|انقص.{0,20}جرعه|اضاعف.{0,20}جرعه|ضعف.{0,20}جرعه|""" +
                """change.{0,20}dose|increase.{0,20}dose|double.{0,20}dose|reduce.{0,20}dose|lower.{0,20}dose"""
        )
        val normalized = EvidenceRetriever.normalize(question)
        return patterns.any { normalized.contains(EvidenceRetriever.normalize(it)) } ||
            doseChange.containsMatchIn(normalized)
    }

    /** Strips markdown links and URLs that are not part of the real evidence. */
    fun sanitizeReply(reply: String, allowedUrls: Set<String>): String {
        var result = MARKDOWN_LINK_REGEX.replace(reply) { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            if (url in allowedUrls) match.value else label
        }
        result = URL_REGEX.replace(result) { match ->
            if (match.value.trimEnd('.') in allowedUrls) match.value else ""
        }
        return result
    }

    /** Ids the model cited, e.g. "…[1] … [3]". */
    fun extractCitedIds(reply: String): Set<Int> =
        CITED_ID_REGEX.findAll(reply).map { it.groupValues[1].toInt() }.toSet()

    /** Citations are ALWAYS built from evidence — never from model text. */
    fun buildCitations(reply: String, evidence: List<EvidenceItem>): List<AssistantCitation> {
        val cited = extractCitedIds(reply)
        val picked = if (cited.isEmpty()) evidence else evidence.filter { it.id in cited }
        return picked.take(6).map {
            AssistantCitation(sourceName = it.sourceName, sourceUrl = it.sourceUrl, sourceDate = it.sourceDate)
        }.distinctBy { it.sourceName + it.sourceUrl }
    }

    /**
     * Produces the final answer: refusal when evidence is insufficient,
     * otherwise the sanitized reply + referral (when needed) + disclaimer,
     * with citations restricted to real evidence.
     */
    fun finalize(question: String, rawReply: String, pack: EvidencePack): AssistantAnswer {
        val language = languageOf(question)
        if (pack.insufficient) {
            return AssistantAnswer(
                reply = refusal(language),
                citations = emptyList(),
                insufficient = true
            )
        }
        val allowedUrls = pack.items.mapNotNull { it.sourceUrl.takeIf { u -> u.isNotBlank() } }.toSet()
        val body = sanitizeReply(rawReply.trim(), allowedUrls)
        val sections = mutableListOf(body.ifBlank { refusal(language) })
        if (needsReferral(question) || needsReferral(rawReply)) {
            sections.add(referral(language))
        }
        if (pack.conflictsNoted) {
            sections.add(
                if (language == "ar") "⚠️ توجد اختلافات بين المصادر الرسمية في المعلومات أعلاه — اعرض التفاصيل وراجع الصيدلي."
                else "⚠️ Official sources disagree on parts of the information above — review the details and ask a pharmacist."
            )
        }
        sections.add(disclaimer(language))
        return AssistantAnswer(
            reply = sections.joinToString("\n\n"),
            citations = buildCitations(body, pack.items),
            insufficient = false
        )
    }
}

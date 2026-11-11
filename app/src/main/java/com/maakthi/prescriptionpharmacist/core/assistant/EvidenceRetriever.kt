package com.maakthi.prescriptionpharmacist.core.assistant

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeResult
import com.maakthi.prescriptionpharmacist.domain.model.EvidenceItem
import com.maakthi.prescriptionpharmacist.domain.model.EvidencePack

/**
 * RAG retrieval: turns a question into an [EvidencePack] drawn ONLY from the
 * local drug database and the official knowledge layer — every item carries
 * source name + link + date. Pure and fully unit-testable.
 */
object EvidenceRetriever {

    private const val MAX_DRUGS = 3
    private const val MAX_ITEMS = 30
    private const val MAX_TEXT = 300

    /** Bilingual topic keywords → drug record fields. */
    val TOPIC_KEYWORDS: Map<String, List<String>> = mapOf(
        "indications" to listOf(
            "indications", "indication", "uses", "used for", "what does", "treat",
            "يستعمل", "يستخدم", "استعمالات", "دواعي", "لعلاج", "ماذا يعالج", "يفيد"
        ),
        "warnings" to listOf(
            "warning", "warnings", "caution", "careful",
            "تحذير", "تحذيرات", "احذر", "انتبه"
        ),
        "contraindications" to listOf(
            "contraindication", "contraindications", "avoid", "should not take",
            "موانع", "موانع الاستعمال", "ممنوع", "يمنع", "لا يستعمل"
        ),
        "sideEffects" to listOf(
            "side effect", "side effects", "adverse", "adverse effects", "harms",
            "الاثار الجانبية", "الأثار الجانبية", "الاعراض الجانبية", "أعراض جانبية", "اضرار"
        ),
        "interactions" to listOf(
            "interaction", "interactions", "interact", "combine", "together",
            "تداخل", "تداخلات", "يتفاعل", "مع دواء"
        ),
        "pregnancyInformation" to listOf(
            "pregnancy", "pregnant", "الحمل", "حامل"
        ),
        "lactationInformation" to listOf(
            "lactation", "breastfeeding", "nursing", "الرضاعة", "مرضع", "ترضع"
        ),
        "strength" to listOf(
            "dose", "dosage", "dosing", "how much", "جرعة", "الجرعة", "جرام", "كم مل",
            "strength", "تركيز", "التركيز"
        ),
        "dosageForm" to listOf(
            "dosage form", "tablet", "capsule", "syrup", "form",
            "شكل", "الشكل الصيدلاني", "قرص", "كبسولة", "شراب", "حقن"
        )
    )

    private val DEFAULT_FIELDS = listOf(
        "indications", "warnings", "sideEffects", "interactions", "contraindications"
    )

    private val FIELD_LABELS: Map<String, Pair<String, String>> = mapOf(
        "indications" to ("الاستطبابات" to "Indications"),
        "warnings" to ("التحذيرات" to "Warnings"),
        "contraindications" to ("موانع الاستعمال" to "Contraindications"),
        "sideEffects" to ("الآثار الجانبية" to "Side effects"),
        "interactions" to ("التداخلات" to "Interactions"),
        "pregnancyInformation" to ("معلومات الحمل" to "Pregnancy"),
        "lactationInformation" to ("معلومات الرضاعة" to "Lactation"),
        "strength" to ("الجرعة/التركيز" to "Dose/strength"),
        "dosageForm" to ("الشكل الصيدلاني" to "Dosage form"),
        "activeIngredient" to ("المادة الفعالة" to "Active ingredient")
    )

    /** Local-DB fallback attribution shown when the record has no source. */
    val LOCAL_SOURCE = "قاعدة البيانات المحلية" to "Local drug database"

    /**
     * Retrieves evidence from the local drug records and (optionally) the
     * official knowledge result. Returns an empty pack when nothing reliable
     * matches — the guard then answers with the explicit refusal.
     */
    fun retrieve(
        question: String,
        drugs: List<Drug>,
        knowledge: DrugKnowledgeResult?,
        language: String? = null
    ): EvidencePack {
        val lang = language ?: AnswerGuard.languageOf(question)
        val normalizedQuestion = normalize(question)
        if (normalizedQuestion.isEmpty()) return EvidencePack(emptyList())

        // ---- drug matching (Arabic + English names) ----
        val matched = drugs.mapNotNull { drug ->
            val score = drugMatchScore(drug, normalizedQuestion)
            if (score > 0) drug to score else null
        }.sortedByDescending { it.second }
            .take(MAX_DRUGS)

        // ---- topic fields ----
        val fields = matchedFields(normalizedQuestion)

        // ---- build items ----
        val items = ArrayList<EvidenceItem>()
        matched.forEach { (drug, _) ->
            val label = drugDisplayName(drug)
            fields.forEach { field ->
                val value = drugField(drug, field)
                if (!value.isNullOrBlank()) {
                    items.add(
                        EvidenceItem(
                            id = items.size + 1,
                            text = truncate("$label — ${fieldLabel(field, lang)}: $value"),
                            sourceName = drug.sourceName ?: LOCAL_SOURCE.let {
                                if (lang == "ar") it.first else it.second
                            },
                            sourceUrl = drug.sourceUrl ?: "",
                            sourceDate = drug.sourceDate ?: ""
                        )
                    )
                }
            }
        }

        // ---- official knowledge facts ----
        var usedKnowledge = false
        var conflictsNoted = false
        if (knowledge != null && knowledge.facts.isNotEmpty()) {
            usedKnowledge = true
            knowledge.facts.forEach { fact ->
                if (items.size < MAX_ITEMS) {
                    items.add(
                        EvidenceItem(
                            id = items.size + 1,
                            text = truncate("${fieldLabel(fact.field, lang)}: ${fact.value}"),
                            sourceName = fact.source.name,
                            sourceUrl = fact.source.url,
                            sourceDate = formatDate(fact.source.retrievedAt)
                        )
                    )
                }
            }
        }
        if (knowledge != null && knowledge.conflicts.isNotEmpty()) {
            conflictsNoted = true
            knowledge.conflicts.forEach { conflict ->
                if (items.size < MAX_ITEMS) {
                    val names = conflict.facts.map { it.source.name }.distinct().joinToString(" / ")
                    items.add(
                        EvidenceItem(
                            id = items.size + 1,
                            text = truncate("⚠ ${fieldLabel(conflict.field, lang)}: ${conflict.facts.joinToString(" | ") { it.value }} ($names)"),
                            sourceName = names,
                            sourceUrl = conflict.facts.firstOrNull()?.source?.url ?: "",
                            sourceDate = formatDate(conflict.facts.firstOrNull()?.source?.retrievedAt ?: 0L)
                        )
                    )
                }
            }
        }

        return EvidencePack(
            items = items.take(MAX_ITEMS),
            matchedDrugs = matched.map { drugDisplayName(it.first) },
            usedKnowledge = usedKnowledge,
            conflictsNoted = conflictsNoted
        )
    }

    /** Extracts a search query for the knowledge layer from the question. */
    fun knowledgeQuery(question: String, drugs: List<Drug>): String {
        val normalizedQuestion = normalize(question)
        val name = drugs.firstOrNull { drugMatchScore(it, normalizedQuestion) > 0 }
            ?.let { drugDisplayName(it) }
        return (name ?: question).take(120)
    }

    // -------------------------------------------------------------------

    private fun drugMatchScore(drug: Drug, normalizedQuestion: String): Int {
        var score = 0
        listOf(
            drug.tradeNameEnglish, drug.tradeNameArabic,
            drug.name, drug.scientificName,
            drug.genericNameEnglish, drug.genericNameArabic,
            drug.activeIngredient
        ).forEach { candidate ->
            val normalized = candidate?.let { normalize(it) } ?: return@forEach
            if (normalized.length >= 3 && normalizedQuestion.contains(normalized)) {
                score += if (normalized.length >= 5) 3 else 2
            }
        }
        return score
    }

    private fun matchedFields(normalizedQuestion: String): List<String> {
        val matched = TOPIC_KEYWORDS.filter { (_, keywords) ->
            keywords.any { normalize(it).let { key -> key.length >= 3 && normalizedQuestion.contains(key) } }
        }.keys.toList()
        return if (matched.isEmpty()) DEFAULT_FIELDS else matched
    }

    private fun drugField(drug: Drug, field: String): String? = when (field) {
        "indications" -> drug.indications
        "warnings" -> drug.warnings
        "contraindications" -> drug.contraindications
        "sideEffects" -> drug.sideEffects
        "interactions" -> drug.interactions
        "pregnancyInformation" -> drug.pregnancyInformation
        "lactationInformation" -> drug.lactationInformation
        "strength" -> drug.strength
        "dosageForm" -> drug.dosageForm ?: drug.form
        "activeIngredient" -> drug.activeIngredient
        else -> null
    }

    private fun drugDisplayName(drug: Drug): String =
        drug.tradeNameEnglish ?: drug.tradeNameArabic
            ?: drug.genericNameEnglish ?: drug.genericNameArabic
            ?: drug.name

    private fun fieldLabel(field: String, language: String): String {
        val pair = FIELD_LABELS[field] ?: (field to field)
        return if (language == "ar") pair.first else pair.second
    }

    private fun formatDate(millis: Long): String =
        if (millis <= 0) "" else java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(millis))

    private fun truncate(text: String): String =
        if (text.length <= MAX_TEXT) text else text.take(MAX_TEXT - 1) + "…"

    /** Lowercase + Alef/teh marbuta normalization + tashkeel removal. */
    internal fun normalize(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            if (isTashkeel(ch)) continue
            val c = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                else -> ch
            }
            sb.append(c.lowercaseChar())
        }
        return sb.toString().trim()
    }

    private fun isTashkeel(ch: Char): Boolean =
        ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640'
}

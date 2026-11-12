package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Anti-hallucination guard: an extracted field is only kept when it is
 * actually grounded in the OCR text. This is what enforces "never invent a
 * medicine name or dosage".
 *
 * Grounding is field-aware: canonical English units/forms/routes are accepted
 * when their Arabic (or English) alias appears in the text — the OCR text may
 * spell "500 مجم" while the pipeline stores "500 mg".
 */
object GroundingFilter {

    private val unitAliases = listOf(
        setOf("mg", "مجم", "ملغم", "ملغ"),
        setOf("mcg", "µg", "ug", "ميكرو"),
        setOf("g", "جم"),
        setOf("ml", "cc", "مل"),
        setOf("iu", "وحدة دولية")
    )

    private val formAliases = mapOf(
        "tablet" to setOf("tablet", "tablets", "tab", "tabs", "قرص", "أقراص", "اقراص", "قرص"),
        "capsule" to setOf("capsule", "capsules", "cap", "caps", "كبسولة", "كبسول", "أكبسولة"),
        "syrup" to setOf("syrup", "شراب"),
        "suspension" to setOf("suspension", "معلق"),
        "injection" to setOf("injection", "ampoule", "vial", "حقنة", "حقول"),
        "cream" to setOf("cream", "كريم"),
        "ointment" to setOf("ointment", "مرهم"),
        "drops" to setOf("drops", "قطرات", "قطرة"),
        "sachet" to setOf("sachet", "كيس"),
        "solution" to setOf("solution", "محلول"),
        "inhaler" to setOf("inhaler", "بخاخ", "استنشاق"),
        "suppository" to setOf("suppository", "تحميلة"),
        "patch" to setOf("patch")
    )

    private val routeAliases = mapOf(
        "oral" to setOf("oral", "فم", "فموي", "بالفم", "mouth"),
        "intravenous" to setOf("intravenous", "iv", "وريدي"),
        "intramuscular" to setOf("intramuscular", "im", "عضلي"),
        "subcutaneous" to setOf("subcutaneous", "sc", "تحت الجلد"),
        "topical" to setOf("topical", "موضعي", "الجلد"),
        "ophthalmic" to setOf("ophthalmic", "عيني", "eye"),
        "otic" to setOf("otic", "أذني", "ear"),
        "nasal" to setOf("nasal", "أنفي"),
        "inhalation" to setOf("inhalation", "استنشاق", "بخاخ"),
        "rectal" to setOf("rectal", "شرجي"),
        "vaginal" to setOf("vaginal", "مهبلي")
    )

    /** True when generic field content appears in the OCR text. */
    fun isGrounded(field: String, ocrText: String): Boolean {
        val fieldTokens = TextNormalizer.tokens(field)
        if (fieldTokens.isEmpty()) return false
        val ocrTokens = TextNormalizer.tokens(ocrText)
        if (ocrTokens.isEmpty()) return false
        return fieldTokens.all { ft ->
            ocrTokens.any { ot ->
                ot == ft || ot.contains(ft) || ft.contains(ot) ||
                    TextNormalizer.similarity(ot, ft) >= 0.8f
            }
        }
    }

    /** Strength is grounded when its numbers appear and its unit has an alias present. */
    fun isStrengthGrounded(strength: String, ocrText: String): Boolean {
        val numbers = Regex("\\d+").findAll(TextNormalizer.normalizeDigits(strength))
            .map { it.value }.toList()
        val textDigits = TextNormalizer.normalizeDigits(ocrText)
        if (numbers.isEmpty()) return isGrounded(strength, ocrText)
        val numbersOk = numbers.all { textDigits.contains(it) }
        if (!numbersOk) return false
        val unitToken = TextNormalizer.normalize(strength).split(" ")
            .lastOrNull { it.any { c -> c.isLetter() } } ?: return true
        val normalizedText = TextNormalizer.normalize(ocrText)
        return normalizedText.contains(unitToken) ||
            unitAliases.any { group ->
                group.any { alias ->
                    val na = TextNormalizer.normalize(alias)
                    na.isNotEmpty() && normalizedText.contains(na)
                } && group.any { TextNormalizer.normalize(it) == unitToken || it.equals(unitToken, ignoreCase = true) }
            }
    }

    fun isFormGrounded(form: String, ocrText: String): Boolean =
        isGrounded(form, ocrText) ||
            (formAliases[form.lowercase()] ?: emptySet()).any { isGrounded(it, ocrText) }

    fun isRouteGrounded(route: String, ocrText: String): Boolean =
        isGrounded(route, ocrText) ||
            (routeAliases[route.lowercase()] ?: emptySet()).any { isGrounded(it, ocrText) }

    /** Keeps only grounded fields; null fields pass through as null. */
    fun keepGrounded(
        strength: String?,
        form: String?,
        dose: String?,
        route: String?,
        duration: String?,
        ocrText: String
    ): GroundedFields = GroundedFields(
        strength = strength?.takeIf { isStrengthGrounded(it, ocrText) },
        form = form?.takeIf { isFormGrounded(it, ocrText) },
        dose = dose?.takeIf { isGrounded(it, ocrText) },
        route = route?.takeIf { isRouteGrounded(it, ocrText) },
        duration = duration?.takeIf { isGrounded(it, ocrText) }
    )

    data class GroundedFields(
        val strength: String?,
        val form: String?,
        val dose: String?,
        val route: String?,
        val duration: String?
    )
}

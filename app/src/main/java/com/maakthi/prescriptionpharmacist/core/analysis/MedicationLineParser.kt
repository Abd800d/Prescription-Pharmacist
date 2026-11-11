package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Stages 5–8 (rule-based): extracts the medication name, strength, dosage
 * form, dose, route and duration from ONE prescription line.
 *
 * Contract: never invents data. Every returned field is a literal span of
 * the input line (or a canonical unit form of it). When nothing can be
 * extracted the field is null and the name falls back to the raw line.
 */
object MedicationLineParser {

    private val strengthRegex = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(mg|mcg|µg|ug|g|ml|cc|iu)\\b" +
            "(?:\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*(mg|mcg|µg|ug|g|ml|cc|iu)\\b)?" +
            "|(\\d+(?:[.,]\\d+)?)\\s*(مجم|ملغم|ملغ|جم|وحدة دولية)",
        RegexOption.IGNORE_CASE
    )

    private val unitCanonical = mapOf(
        "mg" to "mg", "mcg" to "mcg", "µg" to "mcg", "ug" to "mcg",
        "g" to "g", "ml" to "ml", "cc" to "ml", "iu" to "IU",
        "مجم" to "mg", "ملغم" to "mg", "ملغ" to "mg", "جم" to "g",
        "وحدة دولية" to "IU"
    )

    private val formKeywords = listOf(
        "tablet", "tablets", "tabs", "tab", "capsule", "capsules", "caps", "cap",
        "syrup", "suspension", "injection", "ampoule", "ampoules", "vial", "vials",
        "cream", "ointment", "gel", "drops", "sachet", "sachets", "solution",
        "inhaler", "inhalers", "spray", "suppository", "suppositories", "patch",
        "lozenge", "lozenges", "powder", "emulsion", "shampoo", "mouthwash",
        "أقراص", "اقراص", "قرص", "كبسولة", "كبسول", "أكبسولة",
        "شراب", "معلق", "حقنة", "حقول", "محلول", "كريم", "مرهم", "نظام",
        "قطرات", "قطرة", "كيس", "بخاخ", "استنشاق", "تحميلة", "قاموس",
        "مسحوك", "شامبو"
    )

    private val formCanonical = mapOf(
        "tablet" to "tablet", "tablets" to "tablet", "tab" to "tablet", "tabs" to "tablet",
        "أقراص" to "tablet", "اقراص" to "tablet", "قرص" to "tablet",
        "capsule" to "capsule", "capsules" to "capsule", "cap" to "capsule", "caps" to "capsule",
        "كبسولة" to "capsule", "كبسول" to "capsule", "أكبسولة" to "capsule",
        "syrup" to "syrup", "شراب" to "syrup",
        "suspension" to "suspension", "معلق" to "suspension",
        "injection" to "injection", "ampoule" to "injection", "ampoules" to "injection",
        "vial" to "injection", "vials" to "injection",
        "حقنة" to "injection", "حقول" to "injection",
        "cream" to "cream", "كريم" to "cream",
        "ointment" to "ointment", "مرهم" to "ointment", "نظام" to "ointment",
        "gel" to "gel",
        "drops" to "drops", "قطرات" to "drops", "قطرة" to "drops",
        "sachet" to "sachet", "sachets" to "sachet", "كيس" to "sachet",
        "solution" to "solution", "محلول" to "solution",
        "inhaler" to "inhaler", "inhalers" to "inhaler", "بخاخ" to "inhaler", "استنشاق" to "inhaler",
        "spray" to "spray", "suppository" to "suppository", "suppositories" to "suppository",
        "تحميلة" to "suppository",
        "patch" to "patch", "قاموس" to "patch",
        "lozenge" to "lozenge", "lozenges" to "lozenge",
        "powder" to "powder", "emulsion" to "emulsion",
        "shampoo" to "shampoo", "شامبو" to "shampoo",
        "mouthwash" to "mouthwash", "مسحوك" to "mouthwash"
    )

    private val routePatterns = listOf(
        Regex("عن طريق الفم|بالفم|فموي|oral(?:ly)?|\\bpo\\b|by mouth", RegexOption.IGNORE_CASE) to "oral",
        Regex("intravenous|\\biv\\b|وريدي", RegexOption.IGNORE_CASE) to "intravenous",
        Regex("intramuscular|\\bim\\b|عضلي", RegexOption.IGNORE_CASE) to "intramuscular",
        Regex("subcutaneous|\\bsc\\b|تحت الجلد", RegexOption.IGNORE_CASE) to "subcutaneous",
        Regex("topical|موضعي|على الجلد", RegexOption.IGNORE_CASE) to "topical",
        Regex("ophthalmic|عيني|eye drops?", RegexOption.IGNORE_CASE) to "ophthalmic",
        Regex("otic|أذني|ear drops?", RegexOption.IGNORE_CASE) to "otic",
        Regex("nasal|أنفي", RegexOption.IGNORE_CASE) to "nasal",
        Regex("inhal|استنشاق|بخاخ", RegexOption.IGNORE_CASE) to "inhalation",
        Regex("rectal|شرجي", RegexOption.IGNORE_CASE) to "rectal",
        Regex("vaginal|مهبلي", RegexOption.IGNORE_CASE) to "vaginal"
    )

    private val doseRegexes = listOf(
        Regex(
            "(\\d+(?:[.,]\\d+)?)\\s*(tablets?|tabs?|capsules?|caps?|ml|tsp|قرص|أقراص|اقراص|كبسولة|قطرة|قطرات|مل|معلقة|ملعقة|كيس)(?!\\p{L})",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "(قرص|أقراص|اقراص|كبسولة|معلقة|قطرات)\\s*(\\d+(?:[.,]\\d+)?)(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
    )

    private val frequencyRegexes = listOf(
        Regex(
            "once\\s*(a\\s*day|daily)?|twice\\s*(a\\s*day|daily)?|three\\s*times\\s*(a\\s*day|daily)?" +
                "|every\\s*\\d+\\s*(hours?|hrs?)|\\bbd\\b|\\btds\\b|\\bqid\\b|\\bprn\\b|\\bstat\\b|at\\s*bedtime",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "مرتين|ثلاث مرات|ثلاث|أربع مرات|أربع|مرة واحدة|يومياً|يوميا|كل\\s*\\d+\\s*(ساعة|ساعات|يوم|أيام)" +
                "|عند الحاجة|قبل النوم|بعد النوم|قبل الأكل|بعد الأكل|صباحاً|مساءً"
        )
    )

    private val durationRegexes = listOf(
        Regex("for\\s+\\d+\\s*(days?|weeks?|months?)|\\d+\\s*(days?|weeks?|months?)", RegexOption.IGNORE_CASE),
        Regex(
            "لمدة\\s*\\d+\\s*(يوم|أيام|أسبوع|شهر)|\\d+\\s*(يوم|يوماً|أيام|أسبوع|أسبوعين|شهر|أشهر|شهور)" +
                "|أسبوعين"
        )
    )

    private val instructionNoise = Regex(
        "مرتين|ثلاث مرات|ثلاث|أربع مرات|أربع|مرة واحدة|يومياً|يوميا|قبل|بعد|الأكل|النوم|الغداء|الفطور|العشاء" +
            "|عند الحاجة|الحاجة|صباحاً|مساءً|كل|عند|daily|twice|once|morning|night|evening|bedtime" +
            "|every|hours?|hrs?|\\bbd\\b|\\btds\\b|\\bqid\\b|\\bprn\\b|\\bstat\\b|after|before|meals?|with|food",
        RegexOption.IGNORE_CASE
    )

    private val edgeNoise = Regex("^[\\s\\-•*:;.]+|[\\s\\-•*:;.]+$")

    /** Parses one line. Returns null for lines without any letter. */
    fun parse(line: PrescriptionLine): ParsedMedication? = parse(line.text)

    fun parse(text: String): ParsedMedication? {
        if (text.isBlank() || text.none { it.isLetter() }) return null
        val normalizedDigits = TextNormalizer.normalizeDigits(text)

        val strength = extractStrength(normalizedDigits)
        val form = extractForm(normalizedDigits)
        val dose = extractDose(normalizedDigits)
        val route = extractRoute(normalizedDigits)
        val duration = extractDuration(normalizedDigits)
        val name = extractName(normalizedDigits)

        return ParsedMedication(
            name = name,
            strength = strength,
            form = form,
            dose = dose,
            route = route,
            duration = duration,
            rawText = text
        )
    }

    /** True when the name looks ambiguous (question marks, tiny, symbols). */
    fun isUnclearName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.contains('?') || trimmed.contains('؟')) return true
        return trimmed.count { it.isLetter() } < 3
    }

    internal fun extractStrength(text: String): String? {
        val m = strengthRegex.find(text) ?: return null
        val num1 = (m.groups[1]?.value ?: m.groups[5]?.value)?.replace(',', '.')
        val unit1Raw = (m.groups[2]?.value ?: m.groups[6]?.value)?.lowercase()
        val unit1 = unitCanonical[unit1Raw] ?: unit1Raw
        val num2 = m.groups[3]?.value?.replace(',', '.')
        val unit2Raw = m.groups[4]?.value?.lowercase()
        val base = "$num1 $unit1".trim()
        return if (num2 != null && unit2Raw != null) {
            val unit2 = unitCanonical[unit2Raw] ?: unit2Raw
            "$base/$num2 $unit2"
        } else {
            base
        }
    }

    internal fun extractForm(text: String): String? {
        val lower = text.lowercase()
        for (keyword in formKeywords) {
            val regex = Regex("(?<!\\p{L})${Regex.escape(keyword.lowercase())}(?!\\p{L})")
            if (regex.containsMatchIn(lower)) {
                return formCanonical[keyword] ?: keyword
            }
        }
        return null
    }

    internal fun extractDose(text: String): String? {
        for (regex in doseRegexes) {
            val m = regex.find(text)
            if (m != null) return m.value.trim().take(30)
        }
        val frequency = frequencyRegexes.firstNotNullOfOrNull { it.find(text)?.value }
        return frequency?.trim()?.take(30)
    }

    internal fun extractRoute(text: String): String? =
        routePatterns.firstNotNullOfOrNull { (regex, canonical) ->
            if (regex.containsMatchIn(text)) canonical else null
        }

    internal fun extractDuration(text: String): String? =
        durationRegexes.firstNotNullOfOrNull { it.find(text)?.value?.trim() }

    /**
     * Removes every non-name span (strength, dose, frequency, route,
     * duration, form, instructions) directly by regex so canonicalized
     * fields never leave leftovers. What remains is the literal name.
     */
    private fun extractName(text: String): String? {
        var remainder = text
        remainder = strengthRegex.replace(remainder, " ")
        doseRegexes.forEach { remainder = it.replace(remainder, " ") }
        frequencyRegexes.forEach { remainder = it.replace(remainder, " ") }
        durationRegexes.forEach { remainder = it.replace(remainder, " ") }
        routePatterns.forEach { (regex, _) -> remainder = regex.replace(remainder, " ") }
        formKeywords.forEach { keyword ->
            remainder = Regex("(?<!\\p{L})${Regex.escape(keyword)}(?!\\p{L})", RegexOption.IGNORE_CASE)
                .replace(remainder, " ")
        }
        remainder = instructionNoise.replace(remainder, " ")
        // Standalone numbers only — attached digits (Vitamin B12) stay.
        remainder = Regex("(?<!\\p{L})\\d+(?:[.,]\\d+)?(?!\\p{L})").replace(remainder, " ")
        remainder = edgeNoise.replace(remainder.trim(), "")
        remainder = remainder.replace(Regex("\\s+"), " ").trim(' ', '-', '•', '*', ':', ';', '.')
        return remainder.takeIf { it.isNotBlank() }
    }
}

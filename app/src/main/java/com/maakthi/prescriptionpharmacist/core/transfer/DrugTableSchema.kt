package com.maakthi.prescriptionpharmacist.core.transfer

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.RowProblem
import com.maakthi.prescriptionpharmacist.domain.model.RowProblemCode
import java.util.UUID

/**
 * The drug import schema: recognizes Arabic AND English column headers,
 * maps cells to drug fields, and plans each row with per-row problems
 * (missing fields, invalid values, duplicates inside the file or against
 * the existing database).
 *
 * Pure JVM — fully unit-testable.
 */
object DrugTableSchema {

    /** Field keys in canonical export order (matches the drugs table). */
    val EXPORT_FIELDS: List<String> = listOf(
        "tradeNameArabic", "tradeNameEnglish", "genericNameArabic", "genericNameEnglish",
        "activeIngredient", "strength", "dosageForm", "manufacturer", "barcode",
        "indications", "contraindications", "warnings", "sideEffects", "interactions",
        "pregnancyInformation", "lactationInformation", "productImageUrl",
        "sourceName", "sourceUrl", "sourceDate", "createdBy", "isVerified"
    )

    private val HEADER_ALIASES: Map<String, String> = buildMap {
        fun alias(field: String, vararg names: String) {
            names.forEach { put(normalizeHeader(it), field) }
        }
        alias("tradeNameArabic", "tradeNameArabic", "trade name arabic", "الاسم التجاري العربي", "الاسم التجاري (عربي)")
        alias("tradeNameEnglish", "tradeNameEnglish", "trade name english", "name", "الاسم التجاري الانجليزي", "الاسم التجاري الإنجليزي", "الاسم التجاري (انجليزي)")
        alias("genericNameArabic", "genericNameArabic", "generic name arabic", "الاسم العلمي العربي", "الاسم العلمي (عربي)")
        alias("genericNameEnglish", "genericNameEnglish", "generic name english", "scientificName", "الاسم العلمي الانجليزي", "الاسم العلمي الإنجليزي", "الاسم العلمي")
        alias("activeIngredient", "activeIngredient", "active ingredient", "ingredient", "المادة الفعالة", "المادة الفعالة الاساسية")
        alias("strength", "strength", "التركيز", "قوة الدواء")
        alias("dosageForm", "dosageForm", "dosage form", "form", "الشكل الصيدلاني", "الشكل")
        alias("manufacturer", "manufacturer", "الشركة المصنعة", "المصنع", "الشركة")
        alias("barcode", "barcode", "الباركود", "الرمز الشريطي")
        alias("indications", "indications", "الاستطبابات", "دواعي الاستعمال", "المؤشرات")
        alias("contraindications", "contraindications", "موانع الاستعمال", "موانع الاستخدام")
        alias("warnings", "warnings", "التحذيرات", "تحذيرات")
        alias("sideEffects", "sideEffects", "side effects", "الاثار الجانبية", "الأثار الجانبية")
        alias("interactions", "interactions", "التداخلات", "التداخلات الدوائية")
        alias("pregnancyInformation", "pregnancyInformation", "pregnancy", "معلومات الحمل", "الحمل")
        alias("lactationInformation", "lactationInformation", "lactation", "معلومات الرضاعة", "الرضاعة")
        alias("productImageUrl", "productImageUrl", "product image url", "image url", "صورة المنتج", "رابط الصورة")
        alias("sourceName", "sourceName", "source name", "المصدر", "اسم المصدر")
        alias("sourceUrl", "sourceUrl", "source url", "رابط المصدر")
        alias("sourceDate", "sourceDate", "source date", "تاريخ المصدر")
        alias("createdBy", "createdBy", "created by", "المضاف بواسطة", "أضافه", "انشئ بواسطة")
        alias("isVerified", "isVerified", "verified", "موثق", "التوثيق")
    }

    /** Matches a header cell to a field key (null when unrecognized). */
    fun matchHeader(header: String): String? = HEADER_ALIASES[normalizeHeader(header)]

    /** Column index → field key for every recognized column. */
    fun mapColumns(headers: List<String>): Map<Int, String> =
        headers.mapIndexedNotNull { index, header ->
            matchHeader(header)?.let { index to it }
        }.toMap()

    /**
     * Plans one import row: parses values, detects missing fields, invalid
     * values and duplicates. [earlierKeys] carries the duplicate keys of
     * earlier rows in the same file; [existing] is the current database.
     */
    fun planRow(
        rowNumber: Int,
        rawValues: Map<String, String>,
        earlierKeys: Map<String, Int>,
        existing: List<Drug>
    ): ImportRowPlan {
        val values = rawValues.mapValues { it.value.trim() }
        val errors = mutableListOf<RowProblem>()
        val warnings = mutableListOf<RowProblem>()

        val hasName = listOf(
            values["tradeNameArabic"], values["tradeNameEnglish"],
            values["genericNameArabic"], values["genericNameEnglish"]
        ).any { !it.isNullOrBlank() }
        if (!hasName) errors.add(RowProblem(RowProblemCode.MISSING_NAME))

        val barcode = values["barcode"].orEmpty()
        if (barcode.isNotEmpty() && !barcode.all { it.isLetterOrDigit() }) {
            errors.add(RowProblem(RowProblemCode.INVALID_BARCODE, field = "barcode"))
        }

        val verifiedRaw = values["isVerified"].orEmpty()
        if (verifiedRaw.isNotEmpty() && parseBoolean(verifiedRaw) == null) {
            warnings.add(RowProblem(RowProblemCode.INVALID_BOOLEAN, field = "isVerified"))
        }

        // ---- duplicates ----
        val nameKey = duplicateKey(values)
        if (nameKey != null) {
            earlierKeys[nameKey]?.let { earlier ->
                warnings.add(RowProblem(RowProblemCode.DUPLICATE_IN_FILE, detail = earlier.toString()))
            }
        }
        val existingById = existing.firstOrNull { it.barcode != null && it.barcode == barcode }
        val existingByKey = existing.firstOrNull {
            duplicateKey(
                mapOf(
                    "tradeNameArabic" to it.tradeNameArabic.orEmpty(),
                    "tradeNameEnglish" to it.tradeNameEnglish.orEmpty(),
                    "genericNameArabic" to it.genericNameArabic.orEmpty(),
                    "genericNameEnglish" to it.genericNameEnglish.orEmpty(),
                    "strength" to it.strength.orEmpty(),
                    "manufacturer" to it.manufacturer.orEmpty()
                )
            ) == nameKey && nameKey != null
        }
        val duplicateOf = existingById ?: existingByKey
        if (duplicateOf != null) {
            warnings.add(RowProblem(RowProblemCode.DUPLICATE_IN_DB, detail = duplicateOf.id))
        }

        return ImportRowPlan(
            rowNumber = rowNumber,
            values = values,
            errors = errors,
            warnings = warnings,
            included = errors.isEmpty() && warnings.isEmpty()
        )
    }

    /** Builds the drug to persist from an approved row. */
    fun toDrug(values: Map<String, String>, existingId: String?, now: Long): Drug {
        fun v(field: String): String? = values[field]?.trim()?.takeIf { it.isNotEmpty() }
        val tradeEn = v("tradeNameEnglish")
        val tradeAr = v("tradeNameArabic")
        val genericEn = v("genericNameEnglish")
        val genericAr = v("genericNameArabic")
        return Drug(
            id = existingId ?: UUID.randomUUID().toString(),
            name = tradeEn ?: tradeAr ?: genericEn ?: genericAr ?: "",
            scientificName = genericEn ?: genericAr,
            type = when {
                tradeEn != null || tradeAr != null -> MedicationType.BRAND
                genericEn != null || genericAr != null -> MedicationType.GENERIC
                else -> MedicationType.UNKNOWN
            },
            strength = v("strength"),
            form = v("dosageForm"),
            manufacturer = v("manufacturer"),
            indications = v("indications"),
            warnings = v("warnings"),
            isLocal = true,
            tradeNameArabic = tradeAr,
            tradeNameEnglish = tradeEn,
            genericNameArabic = genericAr,
            genericNameEnglish = genericEn,
            activeIngredient = v("activeIngredient"),
            dosageForm = v("dosageForm"),
            barcode = v("barcode"),
            contraindications = v("contraindications"),
            sideEffects = v("sideEffects"),
            interactions = v("interactions"),
            pregnancyInformation = v("pregnancyInformation"),
            lactationInformation = v("lactationInformation"),
            productImageUrl = v("productImageUrl"),
            sourceName = v("sourceName"),
            sourceUrl = v("sourceUrl"),
            sourceDate = v("sourceDate"),
            updatedAt = now,
            createdBy = v("createdBy"),
            isVerified = parseBoolean(v("isVerified").orEmpty()) ?: false
        )
    }

    /** Duplicate key: normalized names + strength + manufacturer. */
    fun duplicateKey(values: Map<String, String>): String? {
        val name = listOf(
            values["tradeNameEnglish"], values["tradeNameArabic"],
            values["genericNameEnglish"], values["genericNameArabic"]
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        if (name.isEmpty()) return null
        return listOf(name, values["strength"].orEmpty(), values["manufacturer"].orEmpty())
            .joinToString("|") { normalizeKey(it) }
    }

    /** Duplicate key for an existing database record. */
    fun duplicateKeyOf(drug: Drug): String? = duplicateKey(
        mapOf(
            "tradeNameEnglish" to drug.tradeNameEnglish.orEmpty(),
            "tradeNameArabic" to drug.tradeNameArabic.orEmpty(),
            "genericNameEnglish" to drug.genericNameEnglish.orEmpty(),
            "genericNameArabic" to drug.genericNameArabic.orEmpty(),
            "strength" to drug.strength.orEmpty(),
            "manufacturer" to drug.manufacturer.orEmpty()
        )
    )

    fun parseBoolean(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true", "1", "yes", "y", "نعم", "موثق", "✓" -> true
        "false", "0", "no", "n", "لا", "غير موثق", "" -> false
        else -> null
    }

    private fun normalizeHeader(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            val c = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                else -> ch
            }
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    private fun normalizeKey(value: String): String =
        normalizeHeader(value)
}

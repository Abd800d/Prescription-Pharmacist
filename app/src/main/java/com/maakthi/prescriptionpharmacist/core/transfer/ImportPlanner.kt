package com.maakthi.prescriptionpharmacist.core.transfer

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.ImportPreview
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat

/**
 * Turns a parsed [TransferTable] (or JSON maps) into an [ImportPreview]:
 * Arabic/English headers are matched, every row is planned with its
 * problems, and duplicates are tracked across the whole file.
 *
 * Nothing is persisted here — import happens only after user approval.
 */
object ImportPlanner {

    fun planTable(
        fileName: String,
        format: TransferFormat,
        table: TransferTable,
        existing: List<Drug>
    ): ImportPreview {
        val columnFields = DrugTableSchema.mapColumns(table.headers)
        val rows = planValues(
            table.rows.mapIndexed { rowIndex, cells ->
                buildMap {
                    columnFields.forEach { (col, field) ->
                        put(field, cells.getOrNull(col).orEmpty())
                    }
                }.let { rowIndex + 1 to it }
            },
            existing
        )
        return ImportPreview(
            fileName = fileName,
            format = format,
            headers = table.headers,
            columnFields = columnFields,
            rows = rows
        )
    }

    /** Plans rows given as (rowNumber → values) pairs — also used for JSON. */
    fun planValues(
        rawRows: List<Pair<Int, Map<String, String>>>,
        existing: List<Drug>
    ): List<ImportRowPlan> {
        val earlierKeys = mutableMapOf<String, Int>()
        return rawRows.map { (rowNumber, values) ->
            val plan = DrugTableSchema.planRow(rowNumber, values, earlierKeys, existing)
            DrugTableSchema.duplicateKey(values)?.let { key ->
                if (key !in earlierKeys) earlierKeys[key] = rowNumber
            }
            plan
        }
    }

    /** Re-plans a single edited row against its neighbours and the database. */
    fun replanRow(
        row: ImportRowPlan,
        newValues: Map<String, String>,
        allRows: List<ImportRowPlan>,
        existing: List<Drug>
    ): ImportRowPlan {
        val earlierKeys = mutableMapOf<String, Int>()
        allRows.filter { it.rowNumber < row.rowNumber }.forEach { earlier ->
            DrugTableSchema.duplicateKey(earlier.values)?.let { key ->
                earlierKeys.putIfAbsent(key, earlier.rowNumber)
            }
        }
        val updated = DrugTableSchema.planRow(row.rowNumber, newValues, earlierKeys, existing)
        // The include/exclude decision is preserved only when the row was
        // valid before the edit (i.e. the checkbox reflected a real choice).
        return when {
            updated.hasBlockingErrors -> updated
            !row.hasBlockingErrors -> updated.copy(included = row.included)
            else -> updated
        }
    }

    /** Encodes one drug record as an export row (canonical field order). */
    fun drugToValues(drug: Drug): Map<String, String> = mapOf(
        "tradeNameArabic" to drug.tradeNameArabic.orEmpty(),
        "tradeNameEnglish" to drug.tradeNameEnglish.orEmpty(),
        "genericNameArabic" to drug.genericNameArabic.orEmpty(),
        "genericNameEnglish" to drug.genericNameEnglish.orEmpty(),
        "activeIngredient" to drug.activeIngredient.orEmpty(),
        "strength" to drug.strength.orEmpty(),
        "dosageForm" to (drug.dosageForm ?: drug.form).orEmpty(),
        "manufacturer" to drug.manufacturer.orEmpty(),
        "barcode" to drug.barcode.orEmpty(),
        "indications" to drug.indications.orEmpty(),
        "contraindications" to drug.contraindications.orEmpty(),
        "warnings" to drug.warnings.orEmpty(),
        "sideEffects" to drug.sideEffects.orEmpty(),
        "interactions" to drug.interactions.orEmpty(),
        "pregnancyInformation" to drug.pregnancyInformation.orEmpty(),
        "lactationInformation" to drug.lactationInformation.orEmpty(),
        "productImageUrl" to drug.productImageUrl.orEmpty(),
        "sourceName" to drug.sourceName.orEmpty(),
        "sourceUrl" to drug.sourceUrl.orEmpty(),
        "sourceDate" to drug.sourceDate.orEmpty(),
        "createdBy" to drug.createdBy.orEmpty(),
        "isVerified" to drug.isVerified.toString()
    )

    fun drugTable(drugs: List<Drug>): TransferTable = TransferTable(
        headers = DrugTableSchema.EXPORT_FIELDS,
        rows = drugs.map { drug ->
            val values = drugToValues(drug)
            DrugTableSchema.EXPORT_FIELDS.map { values[it].orEmpty() }
        }
    )
}

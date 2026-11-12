package com.maakthi.prescriptionpharmacist.domain.model

import kotlinx.serialization.Serializable

/** Supported import/export file formats. */
enum class TransferFormat { CSV, XLSX, JSON }

/** A generated file ready to be saved by the UI. */
data class ExportedFile(
    val suggestedName: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is ExportedFile && other.suggestedName == suggestedName && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = suggestedName.hashCode() * 31 + bytes.contentHashCode()
}

/** Machine-readable row problems; the UI maps them to localized text. */
enum class RowProblemCode {
    MISSING_NAME,
    INVALID_BARCODE,
    INVALID_BOOLEAN,
    DUPLICATE_IN_FILE,
    DUPLICATE_IN_DB
}

data class RowProblem(
    val code: RowProblemCode,
    /** Affected field key (may be null for row-level problems). */
    val field: String? = null,
    /** Extra data, e.g. the number of the earlier duplicated row. */
    val detail: String? = null
)

/**
 * One planned import row: raw values per field key, parsed draft values,
 * blocking [errors], non-blocking [warnings] (duplicates), and the user's
 * include decision. Nothing is written until the user approves the import.
 */
data class ImportRowPlan(
    /** 1-based data-row number shown to the user. */
    val rowNumber: Int,
    /** field key → raw cell value. */
    val values: Map<String, String>,
    val errors: List<RowProblem> = emptyList(),
    val warnings: List<RowProblem> = emptyList(),
    /** Included for import. Rows with errors can never be included. */
    val included: Boolean = true
) {
    val hasBlockingErrors: Boolean get() = errors.isNotEmpty()
}

/**
 * Preview of a parsed import file: headers, per-row plans and totals.
 * Old data is never touched — imports only add or (explicitly) update.
 */
data class ImportPreview(
    val fileName: String,
    val format: TransferFormat,
    val headers: List<String>,
    /** Column index → matched field key (null for unrecognized columns). */
    val columnFields: Map<Int, String>,
    val rows: List<ImportRowPlan>
) {
    val includedRows: List<ImportRowPlan> get() = rows.filter { it.included }
    val rowsWithErrors: Int get() = rows.count { it.hasBlockingErrors }
}

/** One entry of the import/export history. */
@Serializable
data class TransferLogEntry(
    val id: String,
    val action: TransferLogAction,
    val format: String,
    val fileName: String,
    val rowsOk: Int = 0,
    val rowsFailed: Int = 0,
    val encrypted: Boolean = false,
    val performedBy: String = "",
    val timestamp: Long = 0L
)

@Serializable
enum class TransferLogAction { IMPORT, EXPORT, BACKUP, RESTORE }

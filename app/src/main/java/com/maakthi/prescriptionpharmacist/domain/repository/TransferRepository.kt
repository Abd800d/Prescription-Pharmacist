package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ExportedFile
import com.maakthi.prescriptionpharmacist.domain.model.ImportPreview
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat
import com.maakthi.prescriptionpharmacist.domain.model.TransferLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Import/export of the pharmacist's local data.
 *
 * Formats: CSV, XLSX, JSON (+ encrypted backups). Imports are always
 * previewed and approved first and they NEVER delete existing data — they
 * only add new records or (explicitly approved) updates. Every operation is
 * recorded in the transfer history log.
 */
interface TransferRepository {

    // ---- preview & import ----

    /** Parses a picked file (CSV/XLSX/JSON) into an editable preview. */
    suspend fun parsePreview(fileName: String, bytes: ByteArray): AppResult<ImportPreview>

    /**
     * Imports the approved rows. Rows with blocking errors are skipped.
     * Rows flagged as database duplicates perform an explicit update of the
     * matching record; all other rows are inserted with fresh ids.
     */
    suspend fun commitImport(rows: List<ImportRowPlan>, performedBy: String): AppResult<TransferSummary>

    // ---- export ----

    suspend fun exportDrugs(format: TransferFormat): AppResult<ExportedFile>

    suspend fun exportPrescriptions(format: TransferFormat): AppResult<ExportedFile>

    /** Full JSON payload (drugs + prescriptions). */
    suspend fun exportToJson(): AppResult<String>

    // ---- encrypted backup ----

    suspend fun createEncryptedBackup(passphrase: String): AppResult<ExportedFile>

    /** Restores an encrypted backup (merge, never delete). */
    suspend fun restoreEncryptedBackup(passphrase: String, bytes: ByteArray): AppResult<TransferSummary>

    /** Legacy/plain JSON import (merge, never delete). */
    suspend fun importFromJson(json: String): AppResult<TransferSummary>

    // ---- history ----

    fun observeTransferLog(): Flow<List<TransferLogEntry>>
}

data class TransferSummary(
    val prescriptions: Int = 0,
    val drugs: Int = 0,
    val updated: Int = 0
)

package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.core.transfer.BackupCrypto
import com.maakthi.prescriptionpharmacist.core.transfer.CsvCodec
import com.maakthi.prescriptionpharmacist.core.transfer.DrugTableSchema
import com.maakthi.prescriptionpharmacist.core.transfer.ImportPlanner
import com.maakthi.prescriptionpharmacist.core.transfer.TransferFormatException
import com.maakthi.prescriptionpharmacist.core.transfer.TransferTable
import com.maakthi.prescriptionpharmacist.core.transfer.XlsxCodec
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugAuditDao
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.dao.TransferLogDao
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugAuditEntity
import com.maakthi.prescriptionpharmacist.data.local.entity.TransferLogEntity
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.ExportedFile
import com.maakthi.prescriptionpharmacist.domain.model.ImportPreview
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.domain.model.RowProblemCode
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat
import com.maakthi.prescriptionpharmacist.domain.model.TransferLogAction
import com.maakthi.prescriptionpharmacist.domain.model.TransferLogEntry
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TransferPayload(
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val prescriptions: List<Prescription> = emptyList(),
    val drugs: List<Drug> = emptyList()
)

/**
 * Import/export implementation.
 *
 * - CSV / XLSX / JSON parsing into an editable preview (Arabic + English
 *   headers), with per-row errors and duplicate detection;
 * - approved rows are inserted (or explicitly update a duplicate) —
 *   **existing data is never deleted**;
 * - exports for drugs and prescriptions in all three formats;
 * - encrypted backups (PBKDF2 + AES-GCM);
 * - every operation is written to the transfer history log.
 */
@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val drugDao: DrugDao,
    private val prescriptionDao: PrescriptionDao,
    private val drugAuditDao: DrugAuditDao,
    private val transferLogDao: TransferLogDao
) : TransferRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ---------- preview & import ----------

    override suspend fun parsePreview(fileName: String, bytes: ByteArray): AppResult<ImportPreview> {
        return try {
            val format = detectFormat(fileName, bytes)
            val preview = when (format) {
                TransferFormat.CSV -> ImportPlanner.planTable(
                    fileName, format, CsvCodec.parse(String(bytes, Charsets.UTF_8)), existingDrugs()
                )
                TransferFormat.XLSX -> ImportPlanner.planTable(
                    fileName, format, XlsxCodec.read(bytes), existingDrugs()
                )
                TransferFormat.JSON -> planJson(fileName, bytes)
            }
            if (preview.rows.isEmpty()) {
                AppResult.error(ErrorCode.VALIDATION, "empty_file")
            } else {
                AppResult.success(preview)
            }
        } catch (e: TransferFormatException) {
            AppResult.error(ErrorCode.VALIDATION, e.message, e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    override suspend fun commitImport(
        rows: List<ImportRowPlan>,
        performedBy: String
    ): AppResult<TransferSummary> {
        return try {
            val approved = rows.filter { it.included && !it.hasBlockingErrors }
            if (approved.isEmpty()) {
                return AppResult.error(ErrorCode.VALIDATION, "no_rows_selected")
            }
            val now = System.currentTimeMillis()
            var inserted = 0
            var updated = 0
            approved.forEach { row ->
                val duplicateId = row.warnings
                    .firstOrNull { it.code == RowProblemCode.DUPLICATE_IN_DB }?.detail
                val existing = duplicateId?.let { drugDao.getById(it)?.toDomain() }
                // Rows duplicated by barcode resolve to the same record.
                val barcodeOwner = row.values["barcode"]?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { drugDao.getByBarcode(it)?.toDomain() }
                val target = existing ?: barcodeOwner
                val drug = DrugTableSchema.toDrug(row.values, target?.id, now)
                drugDao.upsert(drug.toEntity())
                drugAuditDao.insert(
                    DrugAuditEntity(
                        id = UUID.randomUUID().toString(),
                        drugId = drug.id,
                        action = if (target != null) DrugAuditAction.UPDATE.name else DrugAuditAction.ADD.name,
                        changes = if (target != null) "import update" else "import",
                        performedBy = performedBy,
                        timestamp = now
                    )
                )
                if (target != null) updated++ else inserted++
            }
            val summary = TransferSummary(drugs = inserted, updated = updated)
            log(TransferLogAction.IMPORT, "import", "import", inserted + updated, rows.size - approved.size, encrypted = false, performedBy)
            AppResult.success(summary)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    // ---------- export ----------

    override suspend fun exportDrugs(format: TransferFormat): AppResult<ExportedFile> {
        return exportFile(
            action = TransferLogAction.EXPORT,
            format = format,
            baseName = "drugs",
            table = ImportPlanner.drugTable(existingDrugs()),
            jsonPayload = TransferPayload(exportedAt = System.currentTimeMillis(), drugs = existingDrugs()),
            rows = drugDao.observeAll().first().size,
            encrypted = false
        )
    }

    override suspend fun exportPrescriptions(format: TransferFormat): AppResult<ExportedFile> {
        val prescriptions = prescriptionDao.observeAll().first().map { it.toDomain() }
        return exportFile(
            action = TransferLogAction.EXPORT,
            format = format,
            baseName = "prescriptions",
            table = prescriptionsTable(prescriptions),
            jsonPayload = TransferPayload(exportedAt = System.currentTimeMillis(), prescriptions = prescriptions),
            rows = prescriptions.size,
            encrypted = false
        )
    }

    override suspend fun exportToJson(): AppResult<String> {
        return try {
            val payload = TransferPayload(
                exportedAt = System.currentTimeMillis(),
                prescriptions = prescriptionDao.observeAll().first().map { it.toDomain() },
                drugs = existingDrugs()
            )
            AppResult.success(json.encodeToString(payload))
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }

    // ---------- encrypted backup ----------

    override suspend fun createEncryptedBackup(passphrase: String): AppResult<ExportedFile> {
        return try {
            val text = exportToJson().getOrNull()
                ?: return AppResult.error(ErrorCode.UNKNOWN, "export_failed")
            val encrypted = BackupCrypto.encrypt(passphrase, text.toByteArray(Charsets.UTF_8))
            val file = ExportedFile("prescription-pharmacist-backup.ppbak", encrypted)
            log(TransferLogAction.BACKUP, "backup", file.suggestedName, 0, 0, encrypted = true, "local")
            AppResult.success(file)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    override suspend fun restoreEncryptedBackup(
        passphrase: String,
        bytes: ByteArray
    ): AppResult<TransferSummary> {
        val plain = BackupCrypto.decrypt(passphrase, bytes)
            ?: return AppResult.error(ErrorCode.UNAUTHORIZED, "wrong_passphrase_or_corrupted")
        val summary = importFromJson(String(plain, Charsets.UTF_8))
        if (summary.isSuccess) {
            val s = summary.getOrNull()!!
            log(TransferLogAction.RESTORE, "backup", "restored.ppbak", s.drugs + s.prescriptions, 0, encrypted = true, "local")
        }
        return summary
    }

    override suspend fun importFromJson(jsonText: String): AppResult<TransferSummary> {
        return try {
            val payload = json.decodeFromString<TransferPayload>(jsonText)
            payload.prescriptions.forEach { prescription ->
                prescriptionDao.upsert(
                    prescription.toEntity(),
                    prescription.medications.map { it.toEntity() }
                )
            }
            payload.drugs.forEach { drugDao.upsert(it.toEntity()) }
            AppResult.success(
                TransferSummary(
                    prescriptions = payload.prescriptions.size,
                    drugs = payload.drugs.size
                )
            )
        } catch (e: SerializationException) {
            AppResult.error(ErrorCode.VALIDATION, "Invalid file format", e)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }

    // ---------- history ----------

    override fun observeTransferLog(): Flow<List<TransferLogEntry>> =
        transferLogDao.observeAll().map { list ->
            list.map {
                TransferLogEntry(
                    id = it.id,
                    action = runCatching { TransferLogAction.valueOf(it.action) }
                        .getOrDefault(TransferLogAction.IMPORT),
                    format = it.format,
                    fileName = it.fileName,
                    rowsOk = it.rowsOk,
                    rowsFailed = it.rowsFailed,
                    encrypted = it.encrypted,
                    performedBy = it.performedBy,
                    timestamp = it.timestamp
                )
            }
        }

    // ---------- helpers ----------

    private suspend fun existingDrugs(): List<Drug> =
        drugDao.observeAll().first().map { it.toDomain() }

    private fun detectFormat(fileName: String, bytes: ByteArray): TransferFormat {
        val lower = fileName.lowercase(Locale.ROOT)
        if (lower.endsWith(".xlsx")) return TransferFormat.XLSX
        if (lower.endsWith(".json")) return TransferFormat.JSON
        if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")) {
            return TransferFormat.CSV
        }
        // Fall back to magic bytes.
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return TransferFormat.XLSX
        }
        val first = String(bytes, Charsets.UTF_8).trimStart().firstOrNull()
        if (first == '{' || first == '[') return TransferFormat.JSON
        return TransferFormat.CSV
    }

    private fun planJson(fileName: String, bytes: ByteArray): ImportPreview {
        val element = Json.parseToJsonElement(String(bytes, Charsets.UTF_8))
        val array: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> {
                val drugs = element.entries.firstOrNull { it.key == "drugs" }?.value
                if (drugs is JsonArray) drugs
                else throw TransferFormatException("JSON: no drug records found")
            }
            else -> throw TransferFormatException("JSON: unsupported structure")
        }
        val rawRows = array.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val values = buildMap {
                obj.entries.forEach { entry ->
                    DrugTableSchema.matchHeader(entry.key)?.let { field ->
                        val text = when (val value = entry.value) {
                            is JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                        put(field, text)
                    }
                }
            }
            (index + 1) to values
        }
        val rows = ImportPlanner.planValues(rawRows, existingDrugs())
        return ImportPreview(
            fileName = fileName,
            format = TransferFormat.JSON,
            headers = emptyList(),
            columnFields = emptyMap(),
            rows = rows
        )
    }

    private fun exportFile(
        action: TransferLogAction,
        format: TransferFormat,
        baseName: String,
        table: TransferTable,
        jsonPayload: TransferPayload,
        rows: Int,
        encrypted: Boolean
    ): AppResult<ExportedFile> {
        return try {
            val file = when (format) {
                TransferFormat.CSV -> ExportedFile("$baseName.csv", CsvCodec.writeBytes(table))
                TransferFormat.XLSX -> ExportedFile("$baseName.xlsx", XlsxCodec.write(table))
                TransferFormat.JSON -> ExportedFile(
                    "$baseName.json",
                    json.encodeToString(jsonPayload).toByteArray(Charsets.UTF_8)
                )
            }
            log(action, format.name.lowercase(), file.suggestedName, rows, 0, encrypted, "local")
            AppResult.success(file)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    private fun prescriptionsTable(prescriptions: List<Prescription>): TransferTable {
        val headers = listOf(
            "prescriptionId", "prescriptionDate", "prescriptionStatus", "prescriptionNotes",
            "medicationName", "scientificName", "type", "strength", "form",
            "dose", "route", "duration", "confidence", "isUncertain", "confirmedByPharmacist"
        )
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val rows = ArrayList<List<String>>()
        prescriptions.forEach { prescription ->
            val base = listOf(
                prescription.id,
                date.format(Date(prescription.createdAt)),
                prescription.status.name,
                prescription.notes.orEmpty()
            )
            if (prescription.medications.isEmpty()) {
                rows.add(base + List(headers.size - base.size) { "" })
            } else {
                prescription.medications.forEach { medication ->
                    rows.add(
                        base + listOf(
                            medication.name,
                            medication.scientificName.orEmpty(),
                            medication.type.name,
                            medication.strength.orEmpty(),
                            medication.form.orEmpty(),
                            medication.dose.orEmpty(),
                            medication.route.orEmpty(),
                            medication.duration.orEmpty(),
                            medication.confidence.toString(),
                            medication.isUncertain.toString(),
                            medication.confirmedByPharmacist.toString()
                        )
                    )
                }
            }
        }
        return TransferTable(headers, rows)
    }

    private suspend fun log(
        action: TransferLogAction,
        format: String,
        fileName: String,
        rowsOk: Int,
        rowsFailed: Int,
        encrypted: Boolean,
        performedBy: String
    ) {
        transferLogDao.insert(
            TransferLogEntity(
                id = UUID.randomUUID().toString(),
                action = action.name,
                format = format,
                fileName = fileName,
                rowsOk = rowsOk,
                rowsFailed = rowsFailed,
                encrypted = encrypted,
                performedBy = performedBy,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

package com.maakthi.prescriptionpharmacist.presentation.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ExportedFile
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.RowProblemCode
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat
import com.maakthi.prescriptionpharmacist.domain.model.TransferLogAction
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader
import java.text.DateFormat
import java.util.Date

/**
 * Import/export center: pick a file (CSV/XLSX/JSON), preview and edit rows
 * (missing fields, duplicates, per-row errors), import only after approval,
 * export drugs/prescriptions in all formats, encrypted backups, and the
 * operation history. Imports never delete existing data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val log by viewModel.transferLog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingFile by remember { mutableStateOf<ExportedFile?>(null) }
    var backupDialog by remember { mutableStateOf(false) }
    var restorePassDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<ImportRowPlan?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onFilePicked) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = pendingFile
        if (uri != null && file != null) viewModel.saveToFile(uri, file)
        pendingFile = null
    }

    val backupPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onBackupFilePicked) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransferEvent.FileReady -> {
                    pendingFile = event.file
                    saveLauncher.launch(event.file.suggestedName)
                }
                is TransferEvent.Imported -> snackbarHostState.showSnackbar(
                    context.getString(R.string.transfer_import_done, event.summary.drugs, event.summary.updated)
                )
                is TransferEvent.Failed -> snackbarHostState.showSnackbar(
                    context.getString(transferErrorMessageRes(event.message))
                )
                TransferEvent.Saved -> snackbarHostState.showSnackbar(
                    context.getString(R.string.transfer_saved)
                )
            }
        }
    }

    // ---- row editor dialog ----
    editingRow?.let { row ->
        RowEditDialog(
            row = row,
            onSave = { values ->
                viewModel.editRow(row.rowNumber, values)
                editingRow = null
            },
            onDismiss = { editingRow = null }
        )
    }

    // ---- encrypted backup dialogs ----
    if (backupDialog) {
        PassphraseDialog(
            title = stringResource(R.string.transfer_backup_create),
            confirmLabel = stringResource(R.string.transfer_backup_create),
            onConfirm = { passphrase ->
                backupDialog = false
                viewModel.createBackup(passphrase)
            },
            onDismiss = { backupDialog = false }
        )
    }
    if (restorePassDialog) {
        PassphraseDialog(
            title = stringResource(R.string.transfer_backup_restore),
            confirmLabel = stringResource(R.string.transfer_backup_restore),
            onConfirm = { passphrase ->
                restorePassDialog = false
                viewModel.restoreBackup(passphrase)
            },
            onDismiss = { restorePassDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ================= Import =================
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader(title = stringResource(R.string.transfer_import_section))
                        Text(
                            text = stringResource(R.string.transfer_import_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.transfer_pick_file))
                            }
                            if (importState.preview != null) {
                                OutlinedButton(onClick = viewModel::cancelImport) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }

            val preview = importState.preview
            if (preview != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.transfer_preview_summary,
                                    preview.fileName,
                                    preview.format.name,
                                    importState.rows.size,
                                    importState.includedCount,
                                    importState.errorCount,
                                    importState.warningCount
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.transfer_no_delete_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = viewModel::confirmImport,
                                enabled = importState.includedCount > 0,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(
                                        R.string.transfer_import_confirm,
                                        importState.includedCount
                                    )
                                )
                            }
                        }
                    }
                }

                items(importState.rows, key = { "row-${it.rowNumber}" }) { row ->
                    ImportRowCard(
                        row = row,
                        onToggle = { viewModel.toggleRow(row.rowNumber) },
                        onEdit = { editingRow = row }
                    )
                }
            }

            // ================= Export =================
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader(title = stringResource(R.string.transfer_export_section))
                        Text(
                            text = stringResource(R.string.transfer_export_drugs),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.exportDrugs(TransferFormat.CSV) }) { Text("CSV") }
                            OutlinedButton(onClick = { viewModel.exportDrugs(TransferFormat.XLSX) }) { Text("XLSX") }
                            OutlinedButton(onClick = { viewModel.exportDrugs(TransferFormat.JSON) }) { Text("JSON") }
                        }
                        Text(
                            text = stringResource(R.string.transfer_export_prescriptions),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.exportPrescriptions(TransferFormat.CSV) }) { Text("CSV") }
                            OutlinedButton(onClick = { viewModel.exportPrescriptions(TransferFormat.XLSX) }) { Text("XLSX") }
                            OutlinedButton(onClick = { viewModel.exportPrescriptions(TransferFormat.JSON) }) { Text("JSON") }
                        }
                    }
                }
            }

            // ================= Encrypted backup =================
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionHeader(title = stringResource(R.string.transfer_backup_section))
                        Text(
                            text = stringResource(R.string.transfer_backup_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { backupDialog = true }) {
                                Text(stringResource(R.string.transfer_backup_create))
                            }
                            OutlinedButton(
                                onClick = {
                                    backupPickLauncher.launch(arrayOf("*/*"))
                                    restorePassDialog = true
                                }
                            ) {
                                Text(stringResource(R.string.transfer_backup_restore))
                            }
                        }
                    }
                }
            }

            // ================= History =================
            item { SectionHeader(title = stringResource(R.string.transfer_history_section)) }
            if (log.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.transfer_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(log, key = { it.id }) { entry -> TransferLogCard(entry) }
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun ImportRowCard(
    row: ImportRowPlan,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (row.hasBlockingErrors) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = row.included,
                onCheckedChange = { onToggle() },
                enabled = !row.hasBlockingErrors
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${row.rowNumber} — " + (row.values["tradeNameEnglish"]
                        ?: row.values["tradeNameArabic"]
                        ?: row.values["genericNameEnglish"]
                        ?: row.values["genericNameArabic"]
                        ?: "—"),
                    style = MaterialTheme.typography.titleSmall
                )
                row.values["strength"]?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                row.errors.forEach { problem ->
                    Text(
                        text = problemText(problem),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                row.warnings.forEach { problem ->
                    Text(
                        text = problemText(problem),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.transfer_edit_row))
            }
        }
    }
}

@Composable
private fun problemText(problem: com.maakthi.prescriptionpharmacist.domain.model.RowProblem): String {
    val base = when (problem.code) {
        RowProblemCode.MISSING_NAME -> stringResource(R.string.transfer_error_missing_name)
        RowProblemCode.INVALID_BARCODE -> stringResource(R.string.transfer_error_bad_barcode)
        RowProblemCode.INVALID_BOOLEAN -> stringResource(R.string.transfer_error_bad_boolean)
        RowProblemCode.DUPLICATE_IN_FILE -> stringResource(
            R.string.transfer_warn_dup_file, problem.detail ?: "?"
        )
        RowProblemCode.DUPLICATE_IN_DB -> stringResource(R.string.transfer_warn_dup_db)
    }
    return if (problem.field != null) "${problem.field}: $base" else base
}

@Composable
private fun RowEditDialog(
    row: ImportRowPlan,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var values by remember(row.rowNumber) { mutableStateOf(row.values) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_edit_row) + " #${row.rowNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fields: List<Pair<String, Int>> = listOf(
                    "tradeNameArabic" to R.string.drug_trade_name_arabic,
                    "tradeNameEnglish" to R.string.drug_trade_name_english,
                    "genericNameArabic" to R.string.drug_generic_name_arabic,
                    "genericNameEnglish" to R.string.drug_generic_name_english,
                    "activeIngredient" to R.string.drug_active_ingredient,
                    "strength" to R.string.medication_strength,
                    "dosageForm" to R.string.drug_dosage_form,
                    "manufacturer" to R.string.drug_manufacturer_hint,
                    "barcode" to R.string.drug_barcode
                )
                fields.forEach { (field, label) ->
                    OutlinedTextField(
                        value = values[field].orEmpty(),
                        onValueChange = { v -> values = values + (field to v) },
                        label = { Text(stringResource(label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }) {
                Text(stringResource(R.string.transfer_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PassphraseDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.transfer_backup_passphrase_hint))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.transfer_backup_passphrase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (passphrase.isNotBlank()) onConfirm(passphrase) },
                enabled = passphrase.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TransferLogCard(entry: com.maakthi.prescriptionpharmacist.domain.model.TransferLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when (entry.action) {
                            TransferLogAction.IMPORT -> R.string.transfer_log_import
                            TransferLogAction.EXPORT -> R.string.transfer_log_export
                            TransferLogAction.BACKUP -> R.string.transfer_log_backup
                            TransferLogAction.RESTORE -> R.string.transfer_log_restore
                        }
                    ) + " • " + entry.format + (if (entry.encrypted) " 🔒" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.fileName + " — " +
                        stringResource(R.string.transfer_log_rows, entry.rowsOk, entry.rowsFailed),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = DateFormat.getDateTimeInstance().format(Date(entry.timestamp)) +
                        " • " + entry.performedBy,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun transferErrorMessageRes(code: String): Int = when (code) {
    "unreadable_file" -> R.string.transfer_error_unreadable
    "write_failed" -> R.string.transfer_error_write
    "empty_file" -> R.string.transfer_error_empty
    "no_rows_selected" -> R.string.transfer_error_no_rows
    "wrong_passphrase_or_corrupted" -> R.string.transfer_error_passphrase
    "Invalid file format" -> R.string.transfer_error_invalid
    else -> R.string.transfer_error_generic
}

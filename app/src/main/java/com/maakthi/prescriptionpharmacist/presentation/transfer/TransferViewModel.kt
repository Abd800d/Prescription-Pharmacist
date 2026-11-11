package com.maakthi.prescriptionpharmacist.presentation.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ExportedFile
import com.maakthi.prescriptionpharmacist.domain.model.ImportPreview
import com.maakthi.prescriptionpharmacist.domain.model.ImportRowPlan
import com.maakthi.prescriptionpharmacist.domain.model.TransferFormat
import com.maakthi.prescriptionpharmacist.domain.model.TransferLogEntry
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferSummary
import com.maakthi.prescriptionpharmacist.core.transfer.ImportPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TransferEvent {
    data class FileReady(val file: ExportedFile) : TransferEvent
    data class Imported(val summary: TransferSummary) : TransferEvent
    data class Failed(val message: String) : TransferEvent
    data object Saved : TransferEvent
}

/** Editable preview state for the picked import file. */
data class ImportState(
    val preview: ImportPreview? = null,
    val rows: List<ImportRowPlan> = emptyList(),
    val loading: Boolean = false
) {
    val includedCount: Int get() = rows.count { it.included }
    val errorCount: Int get() = rows.count { it.hasBlockingErrors }
    val warningCount: Int get() = rows.count { it.warnings.isNotEmpty() }
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transferRepository: TransferRepository,
    private val drugEntryRepository: DrugEntryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _events = Channel<TransferEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val transferLog: StateFlow<List<TransferLogEntry>> = transferRepository.observeTransferLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var pendingRestore: ByteArray? = null
    private var existingDrugs: List<com.maakthi.prescriptionpharmacist.domain.model.Drug> = emptyList()

    // ---------- import ----------

    /** Reads the picked file and builds the editable preview. */
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState(loading = true)
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                _importState.value = ImportState()
                _events.send(TransferEvent.Failed("unreadable_file"))
                return@launch
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "import"
            existingDrugs = runCatching { drugEntryRepository.observeDrugs().first() }.getOrDefault(emptyList())
            when (val result = transferRepository.parsePreview(name, bytes)) {
                is AppResult.Success -> {
                    val preview = result.data
                    _importState.value = ImportState(preview = preview, rows = preview.rows)
                }
                is AppResult.Error -> {
                    _importState.value = ImportState()
                    _events.send(TransferEvent.Failed(result.message ?: "parse_failed"))
                }
            }
        }
    }

    fun toggleRow(rowNumber: Int) {
        val rows = _importState.value.rows.map {
            if (it.rowNumber == rowNumber && !it.hasBlockingErrors) it.copy(included = !it.included) else it
        }
        _importState.value = _importState.value.copy(rows = rows)
    }

    /** Applies the user's edits to one row and re-plans it. */
    fun editRow(rowNumber: Int, newValues: Map<String, String>) {
        val state = _importState.value
        val rows = state.rows.map { row ->
            if (row.rowNumber == rowNumber) {
                ImportPlanner.replanRow(row, newValues, state.rows, existingDrugs)
            } else {
                row
            }
        }
        _importState.value = state.copy(rows = rows)
    }

    /** Imports the approved rows after explicit user confirmation. */
    fun confirmImport() {
        val rows = _importState.value.rows.filter { it.included }
        if (rows.isEmpty()) return
        viewModelScope.launch {
            when (val result = transferRepository.commitImport(rows, performedBy())) {
                is AppResult.Success -> {
                    _importState.value = ImportState()
                    _events.send(TransferEvent.Imported(result.data))
                }
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.message ?: "import_failed"))
            }
        }
    }

    fun cancelImport() {
        _importState.value = ImportState()
    }

    // ---------- export ----------

    fun exportDrugs(format: TransferFormat) = export { transferRepository.exportDrugs(format) }

    fun exportPrescriptions(format: TransferFormat) = export { transferRepository.exportPrescriptions(format) }

    private fun export(block: suspend () -> AppResult<ExportedFile>) {
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Success -> _events.send(TransferEvent.FileReady(result.data))
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.message ?: "export_failed"))
            }
        }
    }

    /** Writes a generated file to the destination chosen by the user. */
    fun saveToFile(uri: Uri, file: ExportedFile) {
        viewModelScope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(file.bytes)
                    out.flush()
                } != null
            }.getOrDefault(false)
            _events.send(if (ok) TransferEvent.Saved else TransferEvent.Failed("write_failed"))
        }
    }

    // ---------- encrypted backup ----------

    fun createBackup(passphrase: String) {
        viewModelScope.launch {
            when (val result = transferRepository.createEncryptedBackup(passphrase)) {
                is AppResult.Success -> _events.send(TransferEvent.FileReady(result.data))
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.message ?: "backup_failed"))
            }
        }
    }

    /** Stores the picked backup until the user enters the passphrase. */
    fun onBackupFilePicked(uri: Uri) {
        viewModelScope.launch {
            pendingRestore = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (pendingRestore == null) {
                _events.send(TransferEvent.Failed("unreadable_file"))
            }
        }
    }

    fun restoreBackup(passphrase: String) {
        val bytes = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            when (val result = transferRepository.restoreEncryptedBackup(passphrase, bytes)) {
                is AppResult.Success -> _events.send(TransferEvent.Imported(result.data))
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.message ?: "restore_failed"))
            }
        }
    }

    private suspend fun performedBy(): String =
        runCatching { authRepository.currentUser.first() }.getOrNull()
            ?.let { it.displayName ?: it.uid } ?: "pharmacist"
}

package com.maakthi.prescriptionpharmacist.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict
import com.maakthi.prescriptionpharmacist.domain.model.SyncReport
import com.maakthi.prescriptionpharmacist.domain.model.SyncStatus
import com.maakthi.prescriptionpharmacist.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SyncEvent {
    data class SyncDone(val report: SyncReport) : SyncEvent
    data object BackupDone : SyncEvent
    data object Restored : SyncEvent
    data object PassphraseSaved : SyncEvent
    data class Failed(val code: ErrorCode, val message: String? = null) : SyncEvent
}

/**
 * Sync center: sync now, connection + last-sync status, conflict resolution
 * (never silent), and encrypted manual/automatic backups with restore.
 */
@HiltViewModel
class BackupSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val status: StateFlow<SyncStatus> = syncRepository.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    val conflicts: StateFlow<List<SyncConflict>> = syncRepository.observeConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _hasPassphrase = MutableStateFlow(false)
    val hasPassphrase: StateFlow<Boolean> = _hasPassphrase.asStateFlow()

    private val _events = Channel<SyncEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _hasPassphrase.value = syncRepository.hasBackupPassphrase()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _busy.value = true
            val result = syncRepository.syncNow()
            _busy.value = false
            when (result) {
                is AppResult.Success -> _events.send(SyncEvent.SyncDone(result.data))
                is AppResult.Error -> _events.send(SyncEvent.Failed(result.code, result.message))
            }
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !syncRepository.hasBackupPassphrase()) {
                _events.send(SyncEvent.Failed(ErrorCode.VALIDATION, "backup_passphrase_missing"))
                return@launch
            }
            syncRepository.setAutoBackupEnabled(enabled)
        }
    }

    fun savePassphrase(passphrase: String) {
        viewModelScope.launch {
            syncRepository.setBackupPassphrase(passphrase)
            _hasPassphrase.value = true
            _events.send(SyncEvent.PassphraseSaved)
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _busy.value = true
            val result = syncRepository.backupNow()
            _busy.value = false
            when (result) {
                is AppResult.Success -> _events.send(SyncEvent.BackupDone)
                is AppResult.Error -> _events.send(SyncEvent.Failed(result.code, result.message))
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _busy.value = true
            val result = syncRepository.restoreFromCloud()
            _busy.value = false
            when (result) {
                is AppResult.Success -> _events.send(SyncEvent.Restored)
                is AppResult.Error -> _events.send(SyncEvent.Failed(result.code, result.message))
            }
        }
    }

    fun restoreFromFile(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val bytes: ByteArray? = withContext(Dispatchers.IO) {
                runCatching<ByteArray?> {
                    context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                _events.send(SyncEvent.Failed(ErrorCode.IMAGE_UNREADABLE, "unreadable_file"))
                return@launch
            }
            _busy.value = true
            val result = syncRepository.restoreFromFile(bytes, passphrase)
            _busy.value = false
            when (result) {
                is AppResult.Success -> _events.send(SyncEvent.Restored)
                is AppResult.Error -> _events.send(SyncEvent.Failed(result.code, result.message))
            }
        }
    }

    fun resolveConflict(id: String, choice: ConflictChoice) {
        viewModelScope.launch {
            when (val result = syncRepository.resolveConflict(id, choice)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> _events.send(SyncEvent.Failed(result.code, result.message))
            }
        }
    }
}

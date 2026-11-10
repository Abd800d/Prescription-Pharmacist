package com.maakthi.prescriptionpharmacist.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface BackupEvent {
    data class Success(val count: Int) : BackupEvent
    data class Failed(val code: ErrorCode) : BackupEvent
}

@HiltViewModel
class BackupSyncViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val prescriptionRepository: PrescriptionRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val currentUser: StateFlow<AppUser?> = authRepository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val firebaseAvailable: Boolean = backupRepository.isAvailable()

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Backs up every saved prescription to Firebase Storage. */
    fun backupNow() {
        if (!firebaseAvailable) {
            viewModelScope.launch { _events.send(BackupEvent.Failed(ErrorCode.NOT_CONFIGURED)) }
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val prescriptions = prescriptionRepository.observePrescriptions().first()
            if (prescriptions.isEmpty()) {
                _events.send(BackupEvent.Success(0))
                _busy.value = false
                return@launch
            }
            var ok = 0
            var failed = false
            prescriptions.forEach { prescription ->
                val bytes = withContext(Dispatchers.IO) {
                    ImageUtils.compressForUpload(prescription.imagePath)
                }
                when (backupRepository.backupPrescription(prescription, bytes)) {
                    is AppResult.Success -> ok++
                    is AppResult.Error -> failed = true
                }
            }
            _busy.value = false
            if (failed && ok == 0) {
                _events.send(BackupEvent.Failed(ErrorCode.NETWORK))
            } else {
                _events.send(BackupEvent.Success(ok))
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch { authRepository.signInAnonymously() }
    }
}

package com.maakthi.prescriptionpharmacist.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.security.AuditEvent
import com.maakthi.prescriptionpharmacist.core.security.AuditSanitizer
import com.maakthi.prescriptionpharmacist.data.privacy.AuditLogger
import com.maakthi.prescriptionpharmacist.data.privacy.DeviceDataWiper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PrivacyEvent {
    data object Wiped : PrivacyEvent
    data class Failed(val reason: String? = null) : PrivacyEvent
}

/** Privacy screen actions — the account & data deletion policy. */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val wiper: DeviceDataWiper,
    private val auditLogger: AuditLogger
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<PrivacyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Deletes every local category (see DataDeletionPolicy). */
    fun deleteLocalData() {
        viewModelScope.launch {
            _busy.value = true
            try {
                auditLogger.log(AuditEvent.Privacy(action = "wipe_local", subjectId = "device"))
                wiper.wipeAll()
                _events.send(PrivacyEvent.Wiped)
            } catch (e: Exception) {
                _events.send(PrivacyEvent.Failed(e.message))
            } finally {
                _busy.value = false
            }
        }
    }
}

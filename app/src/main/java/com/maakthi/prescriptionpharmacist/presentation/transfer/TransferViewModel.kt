package com.maakthi.prescriptionpharmacist.presentation.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TransferEvent {
    data class ExportReady(val json: String) : TransferEvent
    data class ImportDone(val summary: TransferSummary) : TransferEvent
    data class Failed(val code: ErrorCode, val detail: String? = null) : TransferEvent
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<TransferEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun export() {
        viewModelScope.launch {
            _busy.value = true
            when (val result = transferRepository.exportToJson()) {
                is AppResult.Success -> _events.send(TransferEvent.ExportReady(result.data))
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.code, result.message))
            }
            _busy.value = false
        }
    }

    fun import(json: String) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = transferRepository.importFromJson(json)) {
                is AppResult.Success -> _events.send(TransferEvent.ImportDone(result.data))
                is AppResult.Error -> _events.send(TransferEvent.Failed(result.code, result.message))
            }
            _busy.value = false
        }
    }
}

package com.maakthi.prescriptionpharmacist.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ChatMessage
import com.maakthi.prescriptionpharmacist.domain.model.ChatRole
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data object Thinking : AssistantUiState
    data class Error(val code: ErrorCode) : AssistantUiState
}

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _uiState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _uiState.value == AssistantUiState.Thinking) return

        val history = _messages.value
        _messages.value = history + ChatMessage(
            role = ChatRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _input.value = ""
        _uiState.value = AssistantUiState.Thinking

        viewModelScope.launch {
            when (val result = assistantRepository.sendMessage(history, text)) {
                is AppResult.Success -> {
                    _messages.value = _messages.value + ChatMessage(
                        role = ChatRole.ASSISTANT,
                        content = result.data,
                        timestamp = System.currentTimeMillis()
                    )
                    _uiState.value = AssistantUiState.Idle
                }
                is AppResult.Error -> {
                    _uiState.value = AssistantUiState.Error(result.code)
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = AssistantUiState.Idle
    }
}

package com.maakthi.prescriptionpharmacist.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.assistant.AnswerGuard
import com.maakthi.prescriptionpharmacist.core.assistant.RateLimiter
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AssistantAnswer
import com.maakthi.prescriptionpharmacist.domain.model.AssistantCitation
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

/** One chat bubble; assistant bubbles carry their citations. */
data class ChatBubble(
    val role: ChatRole,
    val content: String,
    val citations: List<AssistantCitation> = emptyList(),
    val insufficient: Boolean = false,
    val timestamp: Long
)

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data object Thinking : AssistantUiState
    data class Error(val code: ErrorCode) : AssistantUiState
}

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatBubble>>(emptyList())
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _uiState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val limiter = RateLimiter(maxPerWindow = 6, windowMillis = 60_000)

    init {
        viewModelScope.launch {
            assistantRepository.observeLog().collect { entries ->
                if (_messages.value.isEmpty() && entries.isNotEmpty()) {
                    _messages.value = entries.flatMap { entry ->
                        listOf(
                            ChatBubble(
                                role = ChatRole.USER,
                                content = entry.question,
                                timestamp = entry.timestamp
                            ),
                            ChatBubble(
                                role = ChatRole.ASSISTANT,
                                content = entry.answer,
                                citations = entry.citations,
                                insufficient = entry.insufficient,
                                timestamp = entry.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _uiState.value == AssistantUiState.Thinking) return

        // Client-side throttle (the server enforces the authoritative limit).
        if (!limiter.tryAcquire(System.currentTimeMillis())) {
            _uiState.value = AssistantUiState.Error(ErrorCode.RATE_LIMITED)
            return
        }

        val history = _messages.value.map {
            ChatMessage(role = it.role, content = it.content, timestamp = it.timestamp)
        }
        _messages.value = _messages.value + ChatBubble(
            role = ChatRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _input.value = ""
        _uiState.value = AssistantUiState.Thinking

        viewModelScope.launch {
            when (val result = assistantRepository.ask(text, history)) {
                is AppResult.Success -> {
                    val answer: AssistantAnswer = result.data
                    _messages.value = _messages.value + ChatBubble(
                        role = ChatRole.ASSISTANT,
                        content = answer.reply,
                        citations = answer.citations,
                        insufficient = answer.insufficient,
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

    /** Deletes the local conversation log (with its history on screen). */
    fun deleteHistory() {
        viewModelScope.launch {
            assistantRepository.clearLog()
            _messages.value = emptyList()
            _uiState.value = AssistantUiState.Idle
        }
    }

    fun dismissError() {
        _uiState.value = AssistantUiState.Idle
    }
}

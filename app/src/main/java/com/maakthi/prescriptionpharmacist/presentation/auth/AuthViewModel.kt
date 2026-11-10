package com.maakthi.prescriptionpharmacist.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthEvent {
    data object Success : AuthEvent
    data class Error(val code: ErrorCode, val detail: String? = null) : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val firebaseAvailable: Boolean = authRepository.isAvailable()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun login(email: String, password: String) = authenticate {
        authRepository.signInWithEmail(email, password)
    }

    fun register(email: String, password: String) = authenticate {
        authRepository.registerWithEmail(email, password)
    }

    fun signInWithGoogleIdToken(idToken: String) = authenticate {
        authRepository.signInWithGoogleIdToken(idToken)
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            if (firebaseAvailable) {
                authRepository.signInAnonymously()
            }
            _events.send(AuthEvent.Success)
        }
    }

    private fun authenticate(block: suspend () -> AppResult<AppUser>) {
        if (!firebaseAvailable) {
            viewModelScope.launch {
                _events.send(AuthEvent.Error(ErrorCode.NOT_CONFIGURED))
            }
            return
        }
        viewModelScope.launch {
            _loading.value = true
            when (val result = block()) {
                is AppResult.Success -> _events.send(AuthEvent.Success)
                is AppResult.Error -> _events.send(
                    AuthEvent.Error(result.code, result.message)
                )
            }
            _loading.value = false
        }
    }
}

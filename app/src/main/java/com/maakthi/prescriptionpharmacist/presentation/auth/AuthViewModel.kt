package com.maakthi.prescriptionpharmacist.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.auth.AuthErrorMapper
import com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
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
    data class Error(val code: AuthErrorType, val detail: String? = null) : AuthEvent
    data object VerificationEmailSent : AuthEvent
    data object PasswordResetSent : AuthEvent
    data object AccountDeleted : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val firebaseAvailable: Boolean = authRepository.isAvailable()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Whether the signed-in user still has to verify their email. */
    private val _needsVerification = MutableStateFlow(false)
    val needsVerification: StateFlow<Boolean> = _needsVerification.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun login(email: String, password: String) = authenticate {
        authRepository.signInWithEmail(email, password).also { result ->
            _needsVerification.value = result.getOrNull()?.isEmailVerified == false
        }
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

    /** Sends a password-reset email to [email]. */
    fun forgotPassword(email: String) {
        val clean = email.trim()
        if (clean.isEmpty()) {
            viewModelScope.launch {
                _events.send(AuthEvent.Error(AuthErrorType.INVALID_EMAIL))
            }
            return
        }
        viewModelScope.launch {
            _loading.value = true
            when (val result = authRepository.sendPasswordReset(clean)) {
                is AppResult.Success -> _events.send(AuthEvent.PasswordResetSent)
                is AppResult.Error -> _events.send(result.asAuthError())
            }
            _loading.value = false
        }
    }

    /** (Re)sends the email-verification link to the signed-in user. */
    fun resendVerification() {
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AppResult.Success -> _events.send(AuthEvent.VerificationEmailSent)
                is AppResult.Error -> _events.send(result.asAuthError())
            }
        }
    }

    /** Refreshes verification state after the user clicked the link. */
    fun refreshVerificationState() {
        viewModelScope.launch {
            authRepository.reloadUser()
            _needsVerification.value = false
        }
    }

    /** Deletes the account. Errors like REQUIRES_RECENT_LOGIN surface as events. */
    fun deleteAccount() {
        viewModelScope.launch {
            _loading.value = true
            when (val result = authRepository.deleteAccount()) {
                is AppResult.Success -> {
                    authRepository.signOut()
                    _events.send(AuthEvent.AccountDeleted)
                }
                is AppResult.Error -> _events.send(result.asAuthError())
            }
            _loading.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun authenticate(block: suspend () -> AppResult<AppUser>) {
        if (!firebaseAvailable) {
            viewModelScope.launch {
                _events.send(AuthEvent.Error(AuthErrorType.NOT_CONFIGURED))
            }
            return
        }
        viewModelScope.launch {
            _loading.value = true
            when (val result = block()) {
                is AppResult.Success -> _events.send(AuthEvent.Success)
                is AppResult.Error -> _events.send(result.asAuthError())
            }
            _loading.value = false
        }
    }

    private fun AppResult.Error.asAuthError(): AuthEvent.Error =
        AuthEvent.Error(AuthErrorMapper.map(message), message)
}

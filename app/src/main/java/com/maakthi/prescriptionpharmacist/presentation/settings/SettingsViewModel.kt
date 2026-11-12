package com.maakthi.prescriptionpharmacist.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppSettings
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ThemeMode
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    val currentUser: StateFlow<AppUser?> = authRepository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val firebaseAvailable: Boolean = authRepository.isAvailable()

    fun onThemeChanged(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
    }

    fun onLanguageChanged(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    /** Returns true when the URL is accepted and saved. */
    fun saveBackendUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://")) return false
        viewModelScope.launch { settingsRepository.setBackendUrl(trimmed) }
        return true
    }

    fun signInAnonymously() {
        viewModelScope.launch { authRepository.signInAnonymously() }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Role from the Firebase ID-token custom claim. */
    val role: StateFlow<UserRole> = MutableStateFlow(UserRole.USER).also { state ->
        viewModelScope.launch { state.value = authRepository.fetchRole() }
    }

    private val _accountEvents = Channel<AccountEvent>(Channel.BUFFERED)
    val accountEvents = _accountEvents.receiveAsFlow()

    /** (Re)sends the email-verification link. */
    fun resendVerification() {
        viewModelScope.launch {
            val result = authRepository.sendEmailVerification()
            _accountEvents.send(
                if (result.isSuccess) AccountEvent.VerificationSent else AccountEvent.Failed
            )
        }
    }

    /** Deletes the account after explicit UI confirmation. */
    fun deleteAccount() {
        viewModelScope.launch {
            when (val result = authRepository.deleteAccount()) {
                is AppResult.Success -> {
                    authRepository.signOut()
                    _accountEvents.send(AccountEvent.AccountDeleted)
                }
                is AppResult.Error -> _accountEvents.send(AccountEvent.Failed)
            }
        }
    }
}

sealed interface AccountEvent {
    data object VerificationSent : AccountEvent
    data object AccountDeleted : AccountEvent
    data object Failed : AccountEvent
}

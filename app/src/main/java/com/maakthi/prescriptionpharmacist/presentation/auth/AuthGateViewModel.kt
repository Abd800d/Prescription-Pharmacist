package com.maakthi.prescriptionpharmacist.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Auth state for the navigation gate. When Firebase is configured, every
 * internal screen requires a signed-in user; the public routes (splash,
 * onboarding, login, register, Google sign-in) stay reachable.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val firebaseAvailable: Boolean = authRepository.isAvailable()

    val user: StateFlow<AppUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

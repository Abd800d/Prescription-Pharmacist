package com.maakthi.prescriptionpharmacist.presentation.splash

import androidx.lifecycle.ViewModel
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import com.maakthi.prescriptionpharmacist.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    /**
     * Routes to onboarding on first launch, to the login screen when Firebase
     * auth is configured but nobody is signed in, otherwise to home.
     */
    suspend fun decideDestination(): String {
        val settings = settingsRepository.settings.first()
        if (!settings.onboardingDone) return Routes.ONBOARDING
        if (authRepository.isAvailable() && authRepository.currentUser.first() == null) {
            return Routes.LOGIN
        }
        return Routes.HOME
    }
}

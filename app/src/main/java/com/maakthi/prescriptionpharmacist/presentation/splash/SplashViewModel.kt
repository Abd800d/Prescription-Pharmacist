package com.maakthi.prescriptionpharmacist.presentation.splash

import androidx.lifecycle.ViewModel
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import com.maakthi.prescriptionpharmacist.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Routes to onboarding on first launch, otherwise to home. */
    suspend fun decideDestination(): String {
        val settings = settingsRepository.settings.first()
        return if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING
    }
}

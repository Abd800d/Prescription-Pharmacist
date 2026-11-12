package com.maakthi.prescriptionpharmacist.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.privacy.ConsentPolicy
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Consent screen actions (Google Play data-processing consent). */
@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Records the consent moment — required before any image processing. */
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setDataConsentAt(System.currentTimeMillis())
            onDone()
        }
    }

    fun consentVersion(): Int = ConsentPolicy.CONSENT_VERSION
}

package com.maakthi.prescriptionpharmacist

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.core.locale.LocaleManager
import com.maakthi.prescriptionpharmacist.core.theme.PrescriptionPharmacistTheme
import com.maakthi.prescriptionpharmacist.domain.model.ThemeMode
import com.maakthi.prescriptionpharmacist.presentation.navigation.AppNavHost
import com.maakthi.prescriptionpharmacist.presentation.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val layoutDirection =
                if (LocaleManager.isRtl(settings.language, this)) LayoutDirection.Rtl
                else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PrescriptionPharmacistTheme(darkTheme = darkTheme) {
                    AppNavHost(
                        onLanguageSelected = { language ->
                            viewModel.onLanguageChanged(language)
                            LocaleManager.persistAndApply(this, language)
                            recreate()
                        }
                    )
                }
            }
        }
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        // Ensure resources resolve with the persisted locale on all API levels.
        super.applyOverrideConfiguration(
            LocaleManager.localizeConfiguration(this, overrideConfiguration)
        )
    }
}

package com.maakthi.prescriptionpharmacist.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.BuildConfig
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ThemeMode
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader
import com.maakthi.prescriptionpharmacist.presentation.navigation.Routes

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var backendUrl by remember { mutableStateOf(settings.backendUrl) }
    var backendError by remember { mutableStateOf(false) }
    var backendSaved by remember { mutableStateOf(false) }

    LaunchedEffect(settings.backendUrl) {
        if (!backendSaved) backendUrl = settings.backendUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )

        // ---- Appearance ----
        SettingsSection(title = stringResource(R.string.settings_appearance)) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge
            )
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        }
                    ),
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.onThemeChanged(mode) }
                )
            }
        }

        // ---- App screens ----
        SettingsSection(title = stringResource(R.string.settings_general)) {
            SettingsLinkRow(
                label = stringResource(R.string.language_settings_title),
                onClick = { onNavigate(Routes.LANGUAGE_SETTINGS) }
            )
            HorizontalDivider()
            SettingsLinkRow(
                label = stringResource(R.string.backup_title),
                onClick = { onNavigate(Routes.BACKUP) }
            )
            HorizontalDivider()
            SettingsLinkRow(
                label = stringResource(R.string.support_title),
                onClick = { onNavigate(Routes.SUPPORT) }
            )
            HorizontalDivider()
            SettingsLinkRow(
                label = stringResource(R.string.privacy_title),
                onClick = { onNavigate(Routes.PRIVACY) }
            )
            HorizontalDivider()
            SettingsLinkRow(
                label = stringResource(R.string.terms_title),
                onClick = { onNavigate(Routes.TERMS) }
            )
        }

        // ---- Analysis service ----
        SettingsSection(title = stringResource(R.string.settings_analysis)) {
            if (settings.backendUrl.isBlank() && BuildConfig.ANALYSIS_BASE_URL.isBlank()) {
                Text(
                    text = stringResource(R.string.settings_backend_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = backendUrl,
                onValueChange = {
                    backendUrl = it
                    backendError = false
                    backendSaved = false
                },
                label = { Text(stringResource(R.string.settings_backend_url)) },
                placeholder = { Text(stringResource(R.string.settings_backend_hint)) },
                isError = backendError,
                supportingText = {
                    if (backendError) {
                        Text(stringResource(R.string.settings_backend_invalid))
                    } else if (backendSaved) {
                        Text(stringResource(R.string.settings_backend_saved))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val ok = viewModel.saveBackendUrl(backendUrl)
                    backendError = !ok
                    backendSaved = ok
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_backend_save))
            }
        }

        // ---- Account ----
        SettingsSection(title = stringResource(R.string.settings_account)) {
            if (!viewModel.firebaseAvailable) {
                Text(
                    text = stringResource(R.string.settings_firebase_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                currentUser?.let { user ->
                    Text(
                        text = stringResource(R.string.settings_signed_in, user.uid),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentUser == null) {
                        Button(onClick = { viewModel.signInAnonymously() }) {
                            Text(stringResource(R.string.settings_sign_in_anonymous))
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.signOut() }) {
                            Text(stringResource(R.string.settings_sign_out))
                        }
                    }
                }
            }
            SettingsLinkRow(
                label = stringResource(R.string.login_title),
                onClick = { onNavigate(Routes.LOGIN) }
            )
        }

        // ---- About ----
        SettingsSection(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.developer_credit),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

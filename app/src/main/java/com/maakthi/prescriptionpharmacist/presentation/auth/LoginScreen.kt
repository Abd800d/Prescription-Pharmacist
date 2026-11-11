package com.maakthi.prescriptionpharmacist.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.presentation.components.FeatureNotReadyCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
    onGoGoogle: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var forgotOpen by remember { mutableStateOf(false) }
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val needsVerification by viewModel.needsVerification.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.Success -> onLoggedIn()
                is AuthEvent.Error -> {
                    val base = context.getString(authErrorMessage(event.code))
                    snackbarHostState.showSnackbar(
                        if (event.code == com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.UNKNOWN) {
                            event.detail?.let { "$base — $it" } ?: base
                        } else {
                            base
                        }
                    )
                }
                AuthEvent.VerificationEmailSent ->
                    snackbarHostState.showSnackbar(context.getString(R.string.auth_verification_sent))
                AuthEvent.PasswordResetSent ->
                    snackbarHostState.showSnackbar(context.getString(R.string.auth_reset_sent))
                AuthEvent.AccountDeleted -> onBack()
            }
        }
    }

    if (forgotOpen) {
        AlertDialog(
            onDismissRequest = { forgotOpen = false },
            title = { Text(stringResource(R.string.auth_reset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.auth_reset_message))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.email_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgotOpen = false
                        viewModel.forgotPassword(email)
                    }
                ) {
                    Text(stringResource(R.string.auth_reset_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { forgotOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!viewModel.firebaseAvailable) {
                FeatureNotReadyCard(message = stringResource(R.string.auth_not_ready))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email_hint)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_hint)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) return@Button
                    viewModel.login(email.trim(), password)
                },
                enabled = !loading && viewModel.firebaseAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.login_button))
                }
            }

            if (needsVerification) {
                FeatureNotReadyCard(message = stringResource(R.string.auth_verify_notice))
                OutlinedButton(
                    onClick = viewModel::resendVerification,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auth_resend_verification))
                }
            }

            TextButton(
                onClick = { forgotOpen = true },
                enabled = viewModel.firebaseAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auth_reset_title))
            }

            OutlinedButton(
                onClick = onGoGoogle,
                enabled = viewModel.firebaseAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.google_signin_button))
            }

            OutlinedButton(
                onClick = { viewModel.continueAsGuest() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.continue_guest))
            }

            TextButton(onClick = onGoRegister, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.register_link))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

internal fun authErrorMessage(code: com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType): Int =
    when (code) {
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.NOT_CONFIGURED -> R.string.auth_not_ready
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.NETWORK_ERROR -> R.string.error_network
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.WRONG_CREDENTIALS,
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.INVALID_CREDENTIAL -> R.string.auth_error_wrong_credentials
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.EMAIL_EXISTS -> R.string.auth_error_email_exists
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.INVALID_EMAIL -> R.string.auth_error_invalid_email
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.WEAK_PASSWORD -> R.string.auth_error_weak_password
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.USER_NOT_FOUND -> R.string.auth_error_user_not_found
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.USER_DISABLED -> R.string.auth_error_user_disabled
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.TOO_MANY_REQUESTS -> R.string.auth_error_too_many
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.REQUIRES_RECENT_LOGIN -> R.string.auth_error_recent_login
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.EMAIL_NOT_VERIFIED -> R.string.auth_verify_notice
        com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType.UNKNOWN -> R.string.login_failed
    }

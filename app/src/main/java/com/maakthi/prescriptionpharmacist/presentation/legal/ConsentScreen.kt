package com.maakthi.prescriptionpharmacist.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.maakthi.prescriptionpharmacist.R
import kotlinx.coroutines.launch

/**
 * Google Play data-processing consent screen. Shown before the app can send
 * prescription images anywhere: the user must explicitly agree to image and
 * health-data processing. Declining keeps the user here — nothing is sent.
 */
@Composable
fun ConsentScreen(
    onAccepted: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var declined by remember { mutableStateOf(false) }
    val requiredMsg = stringResource(R.string.consent_required_note)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.consent_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Text(
                text = stringResource(R.string.consent_body),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.consent_accuracy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Button(
                onClick = { viewModel.accept(onAccepted) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.consent_accept))
            }

            OutlinedButton(
                onClick = {
                    declined = true
                    scope.launch { snackbarHostState.showSnackbar(requiredMsg) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.consent_decline))
            }

            if (declined) {
                Text(
                    text = requiredMsg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

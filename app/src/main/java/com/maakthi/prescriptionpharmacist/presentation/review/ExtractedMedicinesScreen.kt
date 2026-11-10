package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.presentation.components.ConfidenceChip
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView

/**
 * Read-only list of medications extracted by the AI, each with its
 * confidence score. The pharmacist continues to confirm & edit.
 */
@Composable
fun ExtractedMedicinesScreen(
    onContinue: () -> Unit,
    onReanalyze: () -> Unit,
    viewModel: ReviewFlowViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState as? ReviewUiState.Result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.extracted_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.extracted_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state == null || state.medications.isEmpty()) {
            EmptyStateView(
                message = stringResource(R.string.extracted_empty),
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.medications, key = { it.id }) { medication ->
                    ExtractedMedicationCard(medication)
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.extracted_continue))
        }
        OutlinedButton(
            onClick = onReanalyze,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.review_new_extraction))
        }
    }
}

@Composable
private fun ExtractedMedicationCard(medication: EditableMedication) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = medication.name, style = MaterialTheme.typography.titleMedium)
                    medication.scientificName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (medication.strength != null || medication.form != null) {
                        Text(
                            text = listOfNotNull(medication.strength, medication.form)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ConfidenceChip(
                level = medication.confidenceLevel,
                confidence = medication.confidence
            )
        }
    }
}

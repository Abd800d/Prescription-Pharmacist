package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.presentation.components.ConfidenceChip
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView

/**
 * List of medications extracted by the analysis pipeline, each with its
 * confidence score, uncertainty flag and possible alternatives. The
 * pharmacist continues to confirm & edit — nothing is adopted automatically.
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

        // Mandatory warnings: handwriting may be inaccurate; nothing is
        // dispensed automatically.
        WarningBanner(stringResource(R.string.analysis_warning_handwriting))
        WarningBanner(stringResource(R.string.disclaimer_no_dispense))

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
internal fun WarningBanner(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(10.dp)
    )
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
                    val usage = listOfNotNull(
                        medication.dose?.let { "${stringResource(R.string.label_dose)}: $it" },
                        medication.route?.let { "${stringResource(R.string.label_route)}: $it" },
                        medication.duration?.let { "${stringResource(R.string.label_duration)}: $it" }
                    ).joinToString(" · ")
                    if (usage.isNotEmpty()) {
                        Text(
                            text = usage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfidenceChip(
                    level = medication.confidenceLevel,
                    confidence = medication.confidence
                )
            }
            if (medication.isUncertain) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.label_uncertain),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (medication.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.label_alternatives),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                medication.alternatives.forEach { alt ->
                    Text(
                        text = "• ${alt.name}" +
                            (alt.scientificName?.let { " — $it" } ?: "") +
                            " (${(alt.score * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

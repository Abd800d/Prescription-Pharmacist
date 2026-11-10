package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.core.image.ImageQualityReport
import java.io.File

@Composable
fun PreviewConsentScreen(
    imagePath: String,
    onStartAnalysis: (String) -> Unit,
    onBackToEdit: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(imagePath) { viewModel.load(imagePath) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PreviewEvent.StartAnalysis -> onStartAnalysis(event.imagePath)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.preview_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.preview_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.imagePath != null && File(state.imagePath!!).exists()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = state.imagePath,
                    contentDescription = stringResource(R.string.preview_title),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }

        // Real quality chips.
        state.quality?.let { quality ->
            Text(
                text = stringResource(R.string.preview_quality_title),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QualityChip(
                    label = stringResource(R.string.preview_quality_dimensions),
                    value = "${quality.width}×${quality.height}"
                )
                QualityChip(
                    label = stringResource(R.string.preview_quality_size),
                    value = "${state.uploadSizeKb} KB"
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QualityChip(
                    label = stringResource(R.string.preview_quality_blur),
                    value = stringResource(
                        if (quality.isLikelyBlurry) R.string.preview_quality_bad
                        else R.string.preview_quality_good
                    ),
                    warning = quality.isLikelyBlurry
                )
                QualityChip(
                    label = stringResource(R.string.preview_quality_light),
                    value = stringResource(
                        when {
                            quality.isTooDark -> R.string.preview_quality_dark
                            quality.isTooBright -> R.string.preview_quality_bright
                            else -> R.string.preview_quality_good
                        }
                    ),
                    warning = quality.isTooDark || quality.isTooBright
                )
                QualityChip(
                    label = stringResource(R.string.preview_quality_skew),
                    value = "${quality.skewDegrees}°",
                    warning = quality.skewDegrees != 0
                )
            }

            if (quality.hasWarnings) {
                Text(
                    text = stringResource(R.string.preview_warning_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Consent gate: nothing goes to the server without this.
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.consentGiven,
                    onCheckedChange = viewModel::setConsent
                )
                Text(
                    text = stringResource(R.string.preview_consent),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBackToEdit, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_back_edit))
            }
            Button(
                onClick = viewModel::startAnalysis,
                enabled = state.consentGiven && !state.isLoading && state.imagePath != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.preview_start_analysis))
            }
        }

        if (state.showError) {
            Text(
                text = stringResource(R.string.error_image_unreadable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = viewModel::dismissError) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun QualityChip(label: String, value: String, warning: Boolean = false) {
    val container = if (warning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$label: $value",
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    )
}

package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.presentation.components.ErrorView
import java.io.File

/**
 * Prescription analysis screen: shows the image and real-time analysis
 * progress; advances automatically when results arrive; shows errors with
 * retry otherwise.
 */
@Composable
fun AnalysisScreen(
    onAnalysisDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReviewFlowViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is ReviewUiState.Result) onAnalysisDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.analysis_running),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = File(viewModel.imagePathState),
                contentDescription = stringResource(R.string.analysis_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        when (val state = uiState) {
            is ReviewUiState.Analyzing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.analysis_step_upload),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.analysis_step_read),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.analysis_step_extract),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ReviewUiState.Failed -> {
                ErrorView(
                    message = stringResource(
                        when (state.code) {
                            ErrorCode.NOT_CONFIGURED -> R.string.error_not_configured
                            ErrorCode.NETWORK -> R.string.error_network
                            ErrorCode.UNAUTHORIZED -> R.string.error_unauthorized
                            ErrorCode.SERVER -> R.string.error_server
                            ErrorCode.IMAGE_UNREADABLE -> R.string.error_image_unreadable
                            else -> R.string.error_analysis_failed
                        }
                    ),
                    onRetry = { viewModel.analyze() }
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.back))
                }
            }

            is ReviewUiState.Result -> {
                // Brief transition state while navigation happens.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

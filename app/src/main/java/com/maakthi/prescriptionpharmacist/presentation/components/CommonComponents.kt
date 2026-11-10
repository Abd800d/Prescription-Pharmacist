package com.maakthi.prescriptionpharmacist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.core.theme.ConfidenceHigh
import com.maakthi.prescriptionpharmacist.core.theme.ConfidenceLow
import com.maakthi.prescriptionpharmacist.core.theme.ConfidenceMedium
import com.maakthi.prescriptionpharmacist.domain.model.ConfidenceLevel
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus

/** Colored chip showing the AI confidence for an extracted medication. */
@Composable
fun ConfidenceChip(
    level: ConfidenceLevel,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val (labelRes, color) = when (level) {
        ConfidenceLevel.HIGH -> R.string.confidence_high to ConfidenceHigh
        ConfidenceLevel.MEDIUM -> R.string.confidence_medium to ConfidenceMedium
        ConfidenceLevel.LOW -> R.string.confidence_low to ConfidenceLow
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                R.string.confidence_percent,
                (confidence * 100).toInt().coerceIn(0, 100)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/** Status badge for saved prescriptions. */
@Composable
fun StatusBadge(
    status: PrescriptionStatus,
    modifier: Modifier = Modifier
) {
    val (labelRes, color) = when (status) {
        PrescriptionStatus.PENDING -> R.string.history_status_pending to ConfidenceMedium
        PrescriptionStatus.CONFIRMED -> R.string.history_status_confirmed to ConfidenceHigh
        PrescriptionStatus.REJECTED -> R.string.history_status_rejected to ConfidenceLow
    }
    Text(
        text = stringResource(labelRes),
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
}

/** Full-screen empty / info state. */
@Composable
fun EmptyStateView(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Error banner with optional retry. */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
        onRetry?.let { retry ->
            Text(
                text = stringResource(R.string.retry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = retry)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SuccessMark(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = ConfidenceHigh,
        modifier = modifier
    )
}

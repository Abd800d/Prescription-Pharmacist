package com.maakthi.prescriptionpharmacist.presentation.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import java.io.File

/**
 * Crop & edit screen: rotate 90°, flip horizontally, choose an aspect ratio
 * (center crop), then apply and continue to analysis. All operations really
 * re-render the image file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropEditScreen(
    onBack: () -> Unit,
    onEdited: (String) -> Unit,
    viewModel: CropEditViewModel = hiltViewModel()
) {
    val rotation by viewModel.rotationDegrees.collectAsStateWithLifecycle()
    val flip by viewModel.flipHorizontal.collectAsStateWithLifecycle()
    val aspect by viewModel.aspect.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CropEvent.Saved -> onEdited(event.imagePath)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = File(viewModel.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .graphicsLayer {
                            rotationZ = rotation.toFloat()
                            scaleX = if (flip) -1f else 1f
                        }
                )
            }

            // Transform controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = viewModel::rotateLeft) {
                    Icon(
                        Icons.Filled.RotateLeft,
                        contentDescription = stringResource(R.string.crop_rotate_left),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = viewModel::rotateRight) {
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = stringResource(R.string.crop_rotate_right),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = viewModel::toggleFlip) {
                    Icon(
                        Icons.Filled.Flip,
                        contentDescription = stringResource(R.string.crop_flip),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = viewModel::reset) {
                    Text(stringResource(R.string.crop_reset))
                }
            }

            // Aspect presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropAspect.entries.forEach { preset ->
                    FilterChip(
                        selected = aspect == preset,
                        onClick = { viewModel.setAspect(preset) },
                        label = { Text(stringResource(preset.labelKey.toResId())) },
                        leadingIcon = if (preset != CropAspect.FREE) {
                            { Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }

            Button(
                onClick = viewModel::applyEdits,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(if (saving) R.string.analysis_running else R.string.crop_apply)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun String.toResId(): Int = when (this) {
    "crop_aspect_free" -> R.string.crop_aspect_free
    "crop_aspect_1_1" -> R.string.crop_aspect_1_1
    "crop_aspect_4_3" -> R.string.crop_aspect_4_3
    "crop_aspect_3_4" -> R.string.crop_aspect_3_4
    else -> R.string.crop_aspect_free
}

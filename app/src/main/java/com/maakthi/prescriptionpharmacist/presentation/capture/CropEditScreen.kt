package com.maakthi.prescriptionpharmacist.presentation.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.core.image.CropAspect
import java.io.File

@Composable
fun CropEditScreen(
    imagePath: String,
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: CropEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cropSizes by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    LaunchedEffect(imagePath) { viewModel.load(imagePath) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CropEditEvent.Saved -> onSaved(event.imagePath)
            }
        }
    }

    if (state.showError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error)) },
            text = { Text(stringResource(R.string.error_image_unreadable)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.retry))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.crop_title),
            style = MaterialTheme.typography.headlineSmall
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val density = LocalDensity.current

            // Crop frame sized to the selected aspect, fitted in 88% of the viewport.
            val maxW = maxWidth * 0.88f
            val maxH = maxHeight * 0.88f
            val ratio = when (state.aspect) {
                CropAspect.FREE -> maxW / maxH
                CropAspect.SQUARE -> 1f
                CropAspect.LANDSCAPE -> 4f / 3f
                CropAspect.PORTRAIT -> 3f / 4f
            }
            val frameW = minOf(maxW, maxH * ratio)
            val frameH = frameW / ratio
            val framePx = with(density) {
                frameW.toPx().toInt() to frameH.toPx().toInt()
            }
            SideEffect { cropSizes = framePx }

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.renderedPath != null && File(state.renderedPath!!).exists()) {
                    // The image is laid out to FIT THE FRAME at zoom 1, so the
                    // on-screen geometry matches CropMath's model exactly.
                    Box(
                        modifier = Modifier
                            .size(frameW, frameH)
                            .align(Alignment.Center)
                    ) {
                        AsyncImage(
                            model = state.renderedPath,
                            contentDescription = stringResource(R.string.crop_title),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(state.zoom, state.renderedPath) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        viewModel.setPan(dragAmount.x, dragAmount.y)
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = state.zoom
                                    scaleY = state.zoom
                                    translationX = state.offsetX
                                    translationY = state.offsetY
                                }
                        )
                    }
                } else if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Scrim + frame overlay.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = (size.width - framePx.first) / 2f
                    val top = (size.height - framePx.second) / 2f
                    val scrim = Color.Black.copy(alpha = 0.45f)
                    drawRect(scrim, Offset.Zero, Size(size.width, top))
                    drawRect(
                        scrim,
                        Offset(0f, top + framePx.second),
                        Size(size.width, size.height - top - framePx.second)
                    )
                    drawRect(scrim, Offset.Zero, Size(left, size.height))
                    drawRect(
                        scrim,
                        Offset(left + framePx.first, 0f),
                        Size(size.width - left - framePx.first, size.height)
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(framePx.first.toFloat(), framePx.second.toFloat()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }

                if (state.isLoading && state.renderedPath != null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // Crop aspect presets.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropAspect.entries.forEach { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = { viewModel.setAspect(aspect) },
                    label = {
                        Text(
                            stringResource(
                                when (aspect) {
                                    CropAspect.FREE -> R.string.crop_aspect_free
                                    CropAspect.SQUARE -> R.string.crop_aspect_1_1
                                    CropAspect.LANDSCAPE -> R.string.crop_aspect_4_3
                                    CropAspect.PORTRAIT -> R.string.crop_aspect_3_4
                                }
                            )
                        )
                    }
                )
            }
        }

        // Zoom.
        Text(stringResource(R.string.crop_zoom), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.zoom,
            onValueChange = viewModel::setZoom,
            valueRange = 1f..5f
        )

        // Rotate / flip / reset.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::rotate90) {
                Text(stringResource(R.string.crop_rotate))
            }
            OutlinedButton(onClick = viewModel::flip) {
                Text(stringResource(R.string.crop_flip))
            }
            OutlinedButton(onClick = viewModel::reset) {
                Text(stringResource(R.string.crop_reset))
            }
        }

        // Enhancements.
        Text(stringResource(R.string.crop_enhance), style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(R.string.crop_brightness),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = state.brightness,
            onValueChange = viewModel::setBrightness,
            onValueChangeFinished = viewModel::commitEnhancements,
            valueRange = 0.2f..2f
        )
        Text(
            text = stringResource(R.string.crop_contrast),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = state.contrast,
            onValueChange = viewModel::setContrast,
            onValueChangeFinished = viewModel::commitEnhancements,
            valueRange = 0.2f..2f
        )
        Text(
            text = stringResource(R.string.crop_sharpness),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = state.sharpness,
            onValueChange = viewModel::setSharpness,
            onValueChangeFinished = viewModel::commitEnhancements,
            valueRange = 0f..1f
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::autoEnhance) {
                Text(stringResource(R.string.crop_auto_enhance))
            }
            if (state.skewDegrees != 0) {
                OutlinedButton(onClick = viewModel::autoDeskew) {
                    Text(
                        stringResource(R.string.crop_auto_deskew) + " (${state.skewDegrees}°)"
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val sizes = cropSizes
                    if (sizes != null) {
                        viewModel.save(sizes)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading && state.renderedPath != null
            ) {
                Text(stringResource(R.string.crop_save))
            }
        }
    }
}

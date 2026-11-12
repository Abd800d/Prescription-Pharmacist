package com.maakthi.prescriptionpharmacist.presentation.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.presentation.components.ErrorView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private sealed interface CameraPermissionState {
    data object Granted : CameraPermissionState
    data object Requesting : CameraPermissionState
    data class Denied(val permanently: Boolean) : CameraPermissionState
}

@Composable
fun CaptureScreen(
    onImageReady: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val error by viewModel.error.collectAsStateWithLifecycle()

    var permissionState by remember {
        mutableStateOf<CameraPermissionState>(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                CameraPermissionState.Granted
            } else {
                CameraPermissionState.Requesting
            }
        )
    }
    var askedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) {
            CameraPermissionState.Granted
        } else {
            val activity = context as? android.app.Activity
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            // If the user was asked before and no rationale can be shown, the
            // permission is permanently denied → route to app settings.
            CameraPermissionState.Denied(permanently = askedOnce && !showRationale)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.onGalleryImagePicked(uri?.toString()) }

    // Incremented by the shutter button; observed by the camera preview.
    var captureTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.ImageReady -> onImageReady(event.imagePath)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.capture_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.capture_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = permissionState) {
                CameraPermissionState.Granted -> {
                    CameraPreview(
                        captureTick = captureTick,
                        captureFileFactory = { File(viewModel.createCaptureTarget()) },
                        onPhotoCaptured = viewModel::onPhotoCaptured,
                        onCameraError = { viewModel.onPhotoCaptured(null) }
                    )
                }

                CameraPermissionState.Requesting -> {
                    LaunchedEffect(Unit) {
                        askedOnce = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                is CameraPermissionState.Denied -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(
                                if (state.permanently) R.string.capture_permission_permanent
                                else R.string.capture_permission_denied
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    permissionState = CameraPermissionState.Requesting
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            ) {
                                Text(stringResource(R.string.capture_open_settings))
                            }
                        }
                    }
                }
            }

            error?.let { code ->
                ErrorView(
                    message = stringResource(
                        when (code) {
                            ErrorCode.IMAGE_UNREADABLE -> R.string.error_image_unreadable
                            else -> R.string.error_generic
                        }
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    onRetry = { viewModel.clearError() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (permissionState is CameraPermissionState.Granted) {
                        captureTick++
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.capture_take_photo))
            }
            FilledTonalButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.capture_pick_gallery))
            }
        }
    }
}

@Composable
private fun CameraPreview(
    captureTick: Int,
    captureFileFactory: () -> File,
    onPhotoCaptured: (String?) -> Unit,
    onCameraError: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraBound by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                cameraBound = true
            } catch (e: Exception) {
                onCameraError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraExecutor.shutdown()
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(captureTick) {
        if (captureTick > 0 && cameraBound) {
            val file = captureFileFactory()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        previewView.post { onPhotoCaptured(file.absolutePath) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        previewView.post { onPhotoCaptured(null) }
                    }
                }
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

package com.maakthi.prescriptionpharmacist.presentation.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import com.maakthi.prescriptionpharmacist.domain.model.SyncState
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Saved-image manager: prescription images (with date + saving user, stored
 * encrypted) and pharmaceutical product images. Shows sync status, lets the
 * user add/view/delete images, and enables secure sync. Deleting is
 * permanent (local + remote); the local copy is never removed by syncing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaManagerScreen(
    onBack: () -> Unit,
    viewModel: MediaManagerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailAsset by remember { mutableStateOf<MediaAsset?>(null) }
    var confirmDelete by remember { mutableStateOf<MediaAsset?>(null) }
    var productTitleDialog by remember { mutableStateOf(false) }
    var pendingProductUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val savedMsg = stringResource(R.string.media_saved)
    val deletedMsg = stringResource(R.string.media_deleted)
    val syncMsg = stringResource(R.string.media_sync_done)

    val prescriptionPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::addPrescriptionImage) }
    val productPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingProductUri = uri
            productTitleDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MediaEvent.Saved -> snackbarHostState.showSnackbar(savedMsg)
                MediaEvent.Deleted -> snackbarHostState.showSnackbar(deletedMsg)
                is MediaEvent.Synced -> snackbarHostState.showSnackbar(syncMsg)
                is MediaEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (productTitleDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { productTitleDialog = false },
            title = { Text(stringResource(R.string.media_product_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.media_product_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    productTitleDialog = false
                    pendingProductUri?.let { viewModel.addProductImage(it, title) }
                    pendingProductUri = null
                }) {
                    Text(stringResource(R.string.media_add_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { productTitleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    confirmDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.media_delete_title)) },
            text = { Text(stringResource(R.string.media_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    detailAsset = null
                    viewModel.deleteAsset(asset.id)
                }) {
                    Text(stringResource(R.string.media_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    detailAsset?.let { asset ->
        MediaDetailDialog(
            asset = asset,
            imagePath = state.readablePaths[asset.id],
            onDelete = { confirmDelete = asset },
            onDismiss = { detailAsset = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncNow, enabled = state.syncEnabled) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.media_sync_now))
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
        ) {
            // ---- sync card ----
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.media_sync_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.media_sync_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.syncEnabled,
                        onCheckedChange = viewModel::setSyncEnabled
                    )
                }
            }

            // ---- filters + add ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text(stringResource(R.string.media_filter_all)) }
                )
                FilterChip(
                    selected = state.filter == MediaKind.PRESCRIPTION_IMAGE,
                    onClick = { viewModel.setFilter(MediaKind.PRESCRIPTION_IMAGE) },
                    label = { Text(stringResource(R.string.media_filter_prescriptions)) }
                )
                FilterChip(
                    selected = state.filter == MediaKind.PRODUCT_IMAGE,
                    onClick = { viewModel.setFilter(MediaKind.PRODUCT_IMAGE) },
                    label = { Text(stringResource(R.string.media_filter_products)) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { prescriptionPicker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.media_add_prescription))
                }
                OutlinedButton(onClick = { productPicker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.media_add_product))
                }
            }

            val assets = state.filter?.let { kind ->
                state.assets.filter { it.kind == kind }
            } ?: state.assets

            if (assets.isEmpty()) {
                Text(
                    text = stringResource(R.string.media_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assets, key = { it.id }) { asset ->
                        MediaThumb(
                            asset = asset,
                            imagePath = state.readablePaths[asset.id],
                            onClick = { detailAsset = asset }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(asset: MediaAsset, imagePath: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = imagePath?.let { File(it) },
                contentDescription = asset.title.ifBlank { asset.ownerName },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Text(
                text = asset.title.ifBlank {
                    DateFormat.getDateInstance().format(Date(asset.capturedAt))
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Composable
private fun MediaDetailDialog(
    asset: MediaAsset,
    imagePath: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                asset.title.ifBlank {
                    if (asset.kind == MediaKind.PRESCRIPTION_IMAGE) {
                        stringResource(R.string.media_filter_prescriptions)
                    } else {
                        stringResource(R.string.media_filter_products)
                    }
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = imagePath?.let { File(it) },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Text(
                    text = stringResource(
                        R.string.media_meta,
                        DateFormat.getDateTimeInstance().format(Date(asset.capturedAt)),
                        asset.ownerName
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (asset.syncState) {
                                SyncState.SYNCED -> stringResource(R.string.media_status_synced)
                                SyncState.PENDING -> stringResource(R.string.media_status_pending)
                                SyncState.FAILED -> stringResource(R.string.media_status_failed)
                                SyncState.LOCAL -> stringResource(R.string.media_status_local)
                            }
                        )
                    }
                )
                if (asset.encrypted) {
                    Text(
                        text = stringResource(R.string.media_encrypted_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(stringResource(R.string.media_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

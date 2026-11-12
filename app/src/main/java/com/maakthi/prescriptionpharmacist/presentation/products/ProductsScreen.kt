package com.maakthi.prescriptionpharmacist.presentation.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView
import java.io.File

/**
 * Product images gallery: photos of medicine boxes / shelves with titles,
 * stored locally.
 */
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showTitleDialog by remember { mutableStateOf<Uri?>(null) }
    var captureSourcePath by remember { mutableStateOf<String?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductImage?>(null) }
    var viewingProduct by remember { mutableStateOf<ProductImage?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            captureSourcePath = null
            showTitleDialog = uri
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && captureSourcePath != null) {
            showTitleDialog = Uri.EMPTY
        } else {
            captureSourcePath = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.products_title),
            style = MaterialTheme.typography.headlineSmall
        )

        if (products.isEmpty()) {
            EmptyStateView(
                message = stringResource(R.string.products_empty),
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onClick = { viewingProduct = product },
                        onDelete = { deletingProduct = product }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtendedFloatingActionButton(
                onClick = {
                    val file = ImageUtils.createCaptureFile(context)
                    captureSourcePath = file.absolutePath
                    cameraLauncher.launch(
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file
                        )
                    )
                },
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text(stringResource(R.string.products_add_camera)) }
            )
            ExtendedFloatingActionButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                text = { Text(stringResource(R.string.products_add_gallery)) }
            )
        }
    }

    // Title dialog before saving
    showTitleDialog?.let { uri ->
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showTitleDialog = null
                captureSourcePath = null
            },
            title = { Text(stringResource(R.string.product_title_hint)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addProduct(
                            uri = if (uri == Uri.EMPTY) null else uri,
                            title = title,
                            sourcePath = captureSourcePath
                        )
                        showTitleDialog = null
                        captureSourcePath = null
                    }
                ) {
                    Text(stringResource(R.string.medication_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTitleDialog = null
                        captureSourcePath = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirm
    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text(stringResource(R.string.product_delete_title)) },
            text = { Text(stringResource(R.string.product_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(product.id, product.imagePath)
                        deletingProduct = null
                    }
                ) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Fullscreen viewer
    viewingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { viewingProduct = null },
            title = { Text(product.title) },
            text = {
                Column {
                    AsyncImage(
                        model = File(product.imagePath),
                        contentDescription = product.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingProduct = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun ProductCard(
    product: ProductImage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            AsyncImage(
                model = File(product.imagePath),
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.review_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

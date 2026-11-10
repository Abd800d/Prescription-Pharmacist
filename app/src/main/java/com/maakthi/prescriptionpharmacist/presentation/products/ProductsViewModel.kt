package com.maakthi.prescriptionpharmacist.presentation.products

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import com.maakthi.prescriptionpharmacist.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val products: StateFlow<List<ProductImage>> =
        productRepository.observeProducts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Copies the picked image into app storage and stores a product record. */
    fun addProduct(uri: Uri?, title: String, sourcePath: String? = null) {
        viewModelScope.launch {
            val path = sourcePath ?: uri?.let {
                withContext(Dispatchers.IO) { ImageUtils.copyToAppStorage(context, it) }
            } ?: return@launch
            productRepository.saveProduct(
                ProductImage(
                    id = UUID.randomUUID().toString(),
                    imagePath = path,
                    title = title.trim().ifEmpty { "Product" },
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteProduct(id: String, imagePath: String) {
        viewModelScope.launch {
            productRepository.deleteProduct(id)
            withContext(Dispatchers.IO) { ImageUtils.deleteImage(imagePath) }
        }
    }
}

package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import kotlinx.coroutines.flow.Flow

/** Local product photos (medicine boxes, shelves…). */
interface ProductRepository {
    fun observeProducts(): Flow<List<ProductImage>>
    suspend fun saveProduct(product: ProductImage)
    suspend fun deleteProduct(id: String)
}

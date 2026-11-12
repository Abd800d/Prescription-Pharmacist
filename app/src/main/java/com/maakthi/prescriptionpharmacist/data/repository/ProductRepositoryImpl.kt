package com.maakthi.prescriptionpharmacist.data.repository

import com.maakthi.prescriptionpharmacist.data.local.dao.ProductImageDao
import com.maakthi.prescriptionpharmacist.data.local.mapper.toDomain
import com.maakthi.prescriptionpharmacist.data.local.mapper.toEntity
import com.maakthi.prescriptionpharmacist.domain.model.ProductImage
import com.maakthi.prescriptionpharmacist.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val dao: ProductImageDao
) : ProductRepository {

    override fun observeProducts(): Flow<List<ProductImage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveProduct(product: ProductImage) {
        dao.upsert(product.toEntity())
    }

    override suspend fun deleteProduct(id: String) {
        dao.delete(id)
    }
}

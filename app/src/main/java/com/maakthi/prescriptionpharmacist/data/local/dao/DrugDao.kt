package com.maakthi.prescriptionpharmacist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maakthi.prescriptionpharmacist.data.local.entity.DrugEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Query("SELECT * FROM drugs ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs WHERE id = :id")
    suspend fun getById(id: String): DrugEntity?

    @Query("SELECT * FROM drugs WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): DrugEntity?

    /**
     * Multi-field search: trade names (AR/EN), generic names (AR/EN),
     * active ingredient, barcode and the legacy display name.
     */
    @Query(
        "SELECT * FROM drugs WHERE " +
            "tradeNameArabic LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "tradeNameEnglish LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "genericNameArabic LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "genericNameEnglish LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "activeIngredient LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "barcode LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE ASC"
    )
    suspend fun search(query: String): List<DrugEntity>

    /** Search plus optional filters. Empty filter strings mean "all". */
    @Query(
        "SELECT * FROM drugs WHERE (" +
            "tradeNameArabic LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "tradeNameEnglish LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "genericNameArabic LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "genericNameEnglish LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "activeIngredient LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "barcode LIKE '%' || :query || '%' COLLATE NOCASE OR " +
            "name LIKE '%' || :query || '%' COLLATE NOCASE" +
            ") " +
            "AND (:dosageForm = '' OR dosageForm = :dosageForm) " +
            "AND (:manufacturer = '' OR manufacturer = :manufacturer) " +
            "AND (:verified IS NULL OR isVerified = :verified) " +
            "ORDER BY name COLLATE NOCASE ASC"
    )
    suspend fun searchFiltered(
        query: String,
        dosageForm: String,
        manufacturer: String,
        verified: Int?
    ): List<DrugEntity>

    @Query("SELECT DISTINCT dosageForm FROM drugs WHERE dosageForm IS NOT NULL AND dosageForm != '' ORDER BY dosageForm")
    suspend fun distinctDosageForms(): List<String>

    @Query("SELECT DISTINCT manufacturer FROM drugs WHERE manufacturer IS NOT NULL AND manufacturer != '' ORDER BY manufacturer")
    suspend fun distinctManufacturers(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStrict(drug: DrugEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(drug: DrugEntity)

    @Query("DELETE FROM drugs WHERE id = :id")
    suspend fun delete(id: String)
}

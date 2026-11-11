package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Saved-image library for prescription and product photos.
 *
 * - Prescription images are encrypted at rest (device key) and record the
 *   prescription date + the saving user (stored encrypted).
 * - A local copy ALWAYS exists; sync only uploads an additional secure copy
 *   (per-user Storage paths, never public) when the user enables sync.
 * - Delete is permanent: local file + remote copy + record, all gone.
 */
interface MediaRepository {

    /** Saves a prescription image (compressed, encrypted, dated, attributed). */
    suspend fun savePrescriptionImage(
        imageBytes: ByteArray,
        ownerName: String,
        capturedAt: Long = System.currentTimeMillis()
    ): AppResult<MediaAsset>

    /** Saves a pharmaceutical product image with a title. */
    suspend fun saveProductImage(
        imageBytes: ByteArray,
        title: String,
        capturedAt: Long = System.currentTimeMillis()
    ): AppResult<MediaAsset>

    fun observeAssets(kind: MediaKind? = null): Flow<List<MediaAsset>>

    suspend fun getAsset(id: String): MediaAsset?

    /**
     * Returns a readable (decrypted-if-needed) file for display — only when
     * the viewer passes the access policy. Returns null when denied.
     */
    suspend fun openReadable(assetId: String): AppResult<File>

    /** Permanently deletes the record, the local file and the remote copy. */
    suspend fun deleteAsset(assetId: String): AppResult<Unit>

    /** Uploads pending assets (safe to call on reconnect / app start). */
    suspend fun syncNow(): AppResult<Int>

    /** Toggling sync on marks local copies for upload; off never deletes. */
    suspend fun setSyncEnabled(enabled: Boolean)
}

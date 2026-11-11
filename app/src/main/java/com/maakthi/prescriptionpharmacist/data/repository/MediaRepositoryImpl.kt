package com.maakthi.prescriptionpharmacist.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.storage.FirebaseStorage
import com.maakthi.prescriptionpharmacist.core.media.ImageCompression
import com.maakthi.prescriptionpharmacist.core.media.MediaAccess
import com.maakthi.prescriptionpharmacist.core.media.MediaCrypto
import com.maakthi.prescriptionpharmacist.core.media.SyncPlanner
import com.maakthi.prescriptionpharmacist.data.firebase.FirebaseSupport
import com.maakthi.prescriptionpharmacist.data.local.dao.MediaAssetDao
import com.maakthi.prescriptionpharmacist.data.local.entity.MediaAssetEntity
import com.maakthi.prescriptionpharmacist.data.media.DeviceKeyStore
import com.maakthi.prescriptionpharmacist.data.media.ImageCompressor
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import com.maakthi.prescriptionpharmacist.domain.model.MediaSyncAction
import com.maakthi.prescriptionpharmacist.domain.model.SyncState
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved-image library implementation.
 *
 * Local-first: every image is compressed and stored on-device (prescription
 * images encrypted at rest with the device key, owner name stored encrypted).
 * When the user enables sync and a connection exists, images are uploaded to
 * per-user Firebase Storage paths (private, authenticated) and marked SYNCED.
 * Delete is permanent everywhere. The local copy is never removed by sync.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dao: MediaAssetDao,
    private val deviceKeyStore: DeviceKeyStore,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val firebaseSupport: FirebaseSupport
) : MediaRepository {

    override suspend fun savePrescriptionImage(
        imageBytes: ByteArray,
        ownerName: String,
        capturedAt: Long
    ): AppResult<MediaAsset> = save(
        kind = MediaKind.PRESCRIPTION_IMAGE,
        bytes = imageBytes,
        title = "",
        ownerName = ownerName,
        capturedAt = capturedAt,
        encrypt = true
    )

    override suspend fun saveProductImage(
        imageBytes: ByteArray,
        title: String,
        capturedAt: Long
    ): AppResult<MediaAsset> = save(
        kind = MediaKind.PRODUCT_IMAGE,
        bytes = imageBytes,
        title = title.trim().ifEmpty { "Product" },
        ownerName = currentUserName(),
        capturedAt = capturedAt,
        encrypt = false
    )

    private suspend fun save(
        kind: MediaKind,
        bytes: ByteArray,
        title: String,
        ownerName: String,
        capturedAt: Long,
        encrypt: Boolean
    ): AppResult<MediaAsset> {
        return try {
            val key = deviceKeyStore.keyBytes()
            val compressed = ImageCompressor.compress(bytes)
            val stored = if (encrypt) MediaCrypto.encrypt(key, compressed) else compressed
            val id = UUID.randomUUID().toString()
            val extension = if (encrypt) "bin" else "jpg"
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val file = File(dir, "$id.$extension")
            file.writeBytes(stored)

            val owner = currentUser()
            val syncEnabled = settingsRepository.settings.first().mediaSyncEnabled
            val asset = MediaAsset(
                id = id,
                kind = kind,
                ownerId = owner.uid,
                ownerName = ownerName,
                title = title,
                capturedAt = capturedAt,
                localPath = file.absolutePath,
                remotePath = MediaAccess.remotePathFor(owner.uid, kind.name.lowercase(), id),
                mimeType = "image/jpeg",
                sizeBytes = stored.size.toLong(),
                encrypted = encrypt,
                syncState = if (syncEnabled) SyncState.PENDING else SyncState.LOCAL,
                lastSyncAt = 0
            )
            dao.upsert(asset.toEntity(key))
            AppResult.success(asset)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    override fun observeAssets(kind: MediaKind?): Flow<List<MediaAsset>> {
        val key = runCatching { deviceKeyStore.keyBytes() }.getOrNull()
        val flow = if (kind == null) dao.observeAll() else dao.observeByKind(kind.name)
        return flow.map { list -> list.mapNotNull { it.toDomain(key) } }
    }

    override suspend fun getAsset(id: String): MediaAsset? =
        dao.getById(id)?.toDomain(deviceKeyStore.keyBytes())

    override suspend fun openReadable(assetId: String): AppResult<File> {
        val asset = getAsset(assetId)
            ?: return AppResult.error(ErrorCode.NOT_FOUND)
        val viewer = currentUser()
        if (!MediaAccess.canView(asset, viewer.uid, viewer.role)) {
            return AppResult.error(ErrorCode.UNAUTHORIZED)
        }
        return try {
            val source = File(asset.localPath)
            if (!source.exists()) return AppResult.error(ErrorCode.NOT_FOUND, "local copy missing")
            if (!asset.encrypted) return AppResult.success(source)

            // decrypt to a cache file for display
            val cacheDir = File(context.cacheDir, "media").apply { mkdirs() }
            val cacheFile = File(cacheDir, "${asset.id}.jpg")
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val plain = MediaCrypto.decrypt(deviceKeyStore.keyBytes(), source.readBytes())
                    ?: return AppResult.error(ErrorCode.UNKNOWN, "decrypt failed")
                cacheFile.writeBytes(plain)
            }
            AppResult.success(cacheFile)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    override suspend fun deleteAsset(assetId: String): AppResult<Unit> {
        val asset = getAsset(assetId)
            ?: return AppResult.error(ErrorCode.NOT_FOUND)
        val viewer = currentUser()
        if (!MediaAccess.canDelete(asset, viewer.uid, viewer.role)) {
            return AppResult.error(ErrorCode.UNAUTHORIZED)
        }
        return try {
            // permanent: local file + decrypted cache + remote copy + record
            File(asset.localPath).delete()
            File(context.cacheDir, "media/${asset.id}.jpg").delete()
            if (firebaseSupport.isAvailable && asset.remotePath.isNotBlank()) {
                runCatching {
                    FirebaseStorage.getInstance().reference.child(asset.remotePath).delete().await()
                }
            }
            dao.delete(assetId)
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    override suspend fun syncNow(): AppResult<Int> {
        val syncEnabled = settingsRepository.settings.first().mediaSyncEnabled
        if (!syncEnabled) return AppResult.success(0)
        if (!firebaseSupport.isAvailable) return AppResult.error(ErrorCode.NOT_CONFIGURED)
        if (currentUser().uid == LOCAL_OWNER) return AppResult.error(ErrorCode.UNAUTHORIZED)
        if (!isOnline()) return AppResult.success(0)

        val key = deviceKeyStore.keyBytes()
        var uploaded = 0
        dao.pending().forEach { entity ->
            val asset = entity.toDomain(key) ?: return@forEach
            val action = SyncPlanner.decide(
                syncState = asset.syncState,
                syncEnabled = true,
                isOnline = true,
                now = System.currentTimeMillis(),
                lastAttemptAt = asset.lastSyncAt
            )
            if (action != MediaSyncAction.UPLOAD) return@forEach
            val ok = upload(asset)
            dao.upsert(
                entity.copy(
                    syncState = if (ok) SyncPlanner.afterUpload().name else SyncPlanner.afterFailure().name,
                    lastSyncAt = System.currentTimeMillis()
                )
            )
            if (ok) uploaded++
        }
        return AppResult.success(uploaded)
    }

    private suspend fun upload(asset: MediaAsset): Boolean {
        return try {
            val bytes = File(asset.localPath).readBytes()
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(asset.mimeType)
                .setCustomMetadata("encrypted", asset.encrypted.toString())
                .build()
            FirebaseStorage.getInstance().reference
                .child(asset.remotePath)
                .putBytes(bytes, metadata)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        settingsRepository.setMediaSyncEnabled(enabled)
        if (enabled) {
            val key = deviceKeyStore.keyBytes()
            dao.observeAll().first().forEach { entity ->
                val newState = SyncPlanner.onSyncToggled(true, runCatching { SyncState.valueOf(entity.syncState) }.getOrDefault(SyncState.LOCAL))
                if (newState.name != entity.syncState) {
                    dao.upsert(entity.copy(syncState = newState.name))
                }
            }
            syncNow()
        }
        // Disabling sync never deletes local or remote copies.
    }

    // ---------- helpers ----------

    private suspend fun currentUser(): AppUser {
        val user = runCatching { authRepository.currentUser.first() }.getOrNull()
        val uid = user?.uid?.takeIf { it.isNotBlank() } ?: LOCAL_OWNER
        return (user ?: AppUser(uid = LOCAL_OWNER, isAnonymous = true)).copy(uid = uid)
    }

    private suspend fun currentUserName(): String {
        val user = runCatching { authRepository.currentUser.first() }.getOrNull()
        return user?.displayName ?: user?.email ?: LOCAL_OWNER
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ---------- mapping ----------

    private fun MediaAsset.toEntity(key: ByteArray): MediaAssetEntity = MediaAssetEntity(
        id = id,
        kind = kind.name,
        ownerId = ownerId,
        ownerNameEncrypted = MediaCrypto.encryptString(key, ownerName),
        title = title,
        capturedAt = capturedAt,
        localPath = localPath,
        remotePath = remotePath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        encrypted = encrypted,
        syncState = syncState.name,
        lastSyncAt = lastSyncAt
    )

    private fun MediaAssetEntity.toDomain(key: ByteArray?): MediaAsset? {
        val name = key?.let { MediaCrypto.decryptString(it, ownerNameEncrypted) } ?: return null
        return MediaAsset(
            id = id,
            kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.PRODUCT_IMAGE),
            ownerId = ownerId,
            ownerName = name,
            title = title,
            capturedAt = capturedAt,
            localPath = localPath,
            remotePath = remotePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            encrypted = encrypted,
            syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.LOCAL),
            lastSyncAt = lastSyncAt
        )
    }

    companion object {
        const val LOCAL_OWNER = "local"
    }
}

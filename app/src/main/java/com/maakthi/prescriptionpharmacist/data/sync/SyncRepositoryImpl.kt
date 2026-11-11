package com.maakthi.prescriptionpharmacist.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.storage.FirebaseStorage
import com.maakthi.prescriptionpharmacist.core.sync.BackupPolicy
import com.maakthi.prescriptionpharmacist.core.sync.ConflictResolver
import com.maakthi.prescriptionpharmacist.core.sync.SyncEngine
import com.maakthi.prescriptionpharmacist.data.local.dao.SyncConflictDao
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.ConnectionState
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import com.maakthi.prescriptionpharmacist.domain.model.SyncReport
import com.maakthi.prescriptionpharmacist.domain.model.SyncStatus
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SyncRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first phone↔server sync: drugs and prescriptions through the
 * [SyncEngine] (upserts only, conflicts surfaced to the user), images through
 * the media pipeline, and encrypted backups (manual + automatic) with restore.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    gateway: FirestoreSyncGateway,
    private val localStore: RoomSyncLocalStore,
    private val mediaRepository: MediaRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val syncConflictDao: SyncConflictDao,
    private val backupVault: BackupVault
) : SyncRepository {

    private val engine = SyncEngine(gateway, localStore)

    override suspend fun syncNow(): AppResult<SyncReport> {
        if (!isOnline()) {
            // Offline-first: local data is fully usable; sync waits.
            return AppResult.error(ErrorCode.NETWORK, "offline")
        }
        return try {
            val now = System.currentTimeMillis()
            val report = engine.syncAll(listOf(SyncEntityType.DRUG, SyncEntityType.PRESCRIPTION), now)

            // images: push pending, then pull missing remote copies
            if (!report.interrupted) {
                runCatching { mediaRepository.syncNow() }
                runCatching { mediaRepository.pullRemoteImages() }
                settingsRepository.setLastSyncAt(now)
                autoBackupIfNeeded()
            }
            AppResult.success(report)
        } catch (e: IOException) {
            AppResult.success(SyncReport(interrupted = true))
        } catch (e: Exception) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        }
    }

    override fun observeStatus(): Flow<SyncStatus> = combine(
        settingsRepository.settings,
        syncConflictDao.observeAll(),
        connectionState()
    ) { settings, conflicts, connection ->
        SyncStatus(
            connection = connection,
            lastSyncAt = settings.lastSyncAt,
            lastBackupAt = settings.lastBackupAt,
            pendingConflicts = conflicts.size,
            autoBackupEnabled = settings.autoBackupEnabled
        )
    }

    override fun observeConflicts(): Flow<List<SyncConflict>> =
        syncConflictDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun resolveConflict(conflictId: String, choice: ConflictChoice): AppResult<Unit> {
        val entity = syncConflictDao.getById(conflictId)
            ?: return AppResult.error(ErrorCode.NOT_FOUND)
        val conflict = entity.toDomain()
        val resolution = ConflictResolver.resolve(conflict, choice, System.currentTimeMillis())
        // apply the winning version through the same local pipeline (upsert)
        localStore.applyResolution(
            conflict.entityType,
            conflict.entityId,
            SyncRecord(conflict.entityId, resolution.winningUpdatedAt, resolution.winningJson)
        )
        return AppResult.success(Unit)
    }

    // ---------- backups ----------

    override suspend fun backupNow(): AppResult<Unit> {
        val passphrase = backupVault.passphrase()
            ?: return AppResult.error(ErrorCode.VALIDATION, "backup_passphrase_missing")
        return createEncryptedBackup(passphrase)
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        settingsRepository.setAutoBackupEnabled(enabled)
    }

    override suspend fun setBackupPassphrase(passphrase: String) {
        backupVault.setPassphrase(passphrase)
    }

    override suspend fun hasBackupPassphrase(): Boolean = backupVault.hasPassphrase()

    override suspend fun restoreFromCloud(): AppResult<Unit> {
        val passphrase = backupVault.passphrase()
            ?: return AppResult.error(ErrorCode.VALIDATION, "backup_passphrase_missing")
        return try {
            val uid = currentUid()
            if (uid.isBlank()) return AppResult.error(ErrorCode.UNAUTHORIZED)
            val bytes = FirebaseStorage.getInstance()
                .reference.child("users/$uid/backups/latest.ppbak")
                .getBytes(20L * 1024 * 1024)
                .await()
            transferRepository.restoreEncryptedBackup(passphrase, bytes)
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.NETWORK, cause = e)
        }
    }

    override suspend fun restoreFromFile(bytes: ByteArray, passphrase: String): AppResult<Unit> {
        return when (val result = transferRepository.restoreEncryptedBackup(passphrase, bytes)) {
            is AppResult.Success -> AppResult.success(Unit)
            is AppResult.Error -> AppResult.error(result.code, result.message)
        }
    }

    private suspend fun createEncryptedBackup(passphrase: String): AppResult<Unit> {
        return try {
            val file = transferRepository.createEncryptedBackup(passphrase)
            if (file is AppResult.Success) {
                // encrypted cloud copy — private per-user path only
                runCatching {
                    val uid = currentUid()
                    if (uid.isNotBlank()) {
                        FirebaseStorage.getInstance()
                            .reference.child("users/$uid/backups/latest.ppbak")
                            .putBytes(file.data.bytes)
                            .await()
                    }
                }
                settingsRepository.setLastBackupAt(System.currentTimeMillis())
                AppResult.success(Unit)
            } else {
                AppResult.error(ErrorCode.SERVER, "backup_failed")
            }
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, e.message, e)
        }
    }

    private suspend fun autoBackupIfNeeded() {
        val settings = settingsRepository.settings.first()
        val should = BackupPolicy.shouldAutoBackup(
            autoBackupEnabled = settings.autoBackupEnabled,
            now = System.currentTimeMillis(),
            lastBackupAt = settings.lastBackupAt,
            lastSyncAt = settings.lastSyncAt
        )
        if (should && backupVault.hasPassphrase()) {
            runCatching { backupNow() }
        }
    }

    // ---------- helpers ----------

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun connectionState(): Flow<ConnectionState> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            trySend(ConnectionState.UNKNOWN)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(ConnectionState.ONLINE)
            }

            override fun onLost(network: Network) {
                trySend(ConnectionState.OFFLINE)
            }
        }
        trySend(if (isOnline()) ConnectionState.ONLINE else ConnectionState.OFFLINE)
        manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    private suspend fun currentUid(): String =
        runCatching { authRepository.currentUser.first()?.uid }.getOrNull().orEmpty()
}

private fun com.maakthi.prescriptionpharmacist.data.local.entity.SyncConflictEntity.toDomain(): SyncConflict =
    SyncConflict(
        id = id,
        entityType = runCatching { SyncEntityType.valueOf(entityType) }.getOrDefault(SyncEntityType.DRUG),
        entityId = entityId,
        label = label,
        localUpdatedAt = localUpdatedAt,
        remoteUpdatedAt = remoteUpdatedAt,
        localJson = localJson,
        remoteJson = remoteJson,
        detectedAt = detectedAt
    )

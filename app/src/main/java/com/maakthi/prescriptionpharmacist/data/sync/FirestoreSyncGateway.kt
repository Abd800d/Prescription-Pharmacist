package com.maakthi.prescriptionpharmacist.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.maakthi.prescriptionpharmacist.core.sync.SyncGateway
import com.maakthi.prescriptionpharmacist.domain.model.SyncEntityType
import com.maakthi.prescriptionpharmacist.domain.model.SyncRecord
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore transport for phone↔server sync. Records live under the owner's
 * private space: users/{uid}/sync/{type}/{id} — enforced by firestore.rules
 * (owner-only). Uploads are upserts; sync never deletes.
 */
@Singleton
class FirestoreSyncGateway @Inject constructor(
    private val authRepository: AuthRepository
) : SyncGateway {

    private suspend fun collection(type: SyncEntityType): com.google.firebase.firestore.CollectionReference {
        val uid = runCatching { authRepository.currentUser.first()?.uid }.getOrNull().orEmpty()
        if (uid.isBlank()) throw IOException("not signed in")
        return FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("sync").document(type.name.lowercase())
            .collection("records")
    }

    override suspend fun push(type: SyncEntityType, records: List<SyncRecord>) {
        try {
            val ref = collection(type)
            val batch = FirebaseFirestore.getInstance().batch()
            records.forEach { record ->
                batch.set(
                    ref.document(record.id),
                    mapOf(
                        "id" to record.id,
                        "updatedAt" to record.updatedAt,
                        "payload" to record.payloadJson
                    )
                )
            }
            batch.commit().await()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("push failed: ${e.message}", e)
        }
    }

    override suspend fun pull(type: SyncEntityType, since: Long): List<SyncRecord> {
        return try {
            val snapshot: QuerySnapshot = collection(type).get().await()
            snapshot.documents.mapNotNull { doc ->
                val updatedAt = (doc.getLong("updatedAt") ?: return@mapNotNull null)
                if (updatedAt <= since) return@mapNotNull null
                SyncRecord(
                    id = doc.getString("id") ?: doc.id,
                    updatedAt = updatedAt,
                    payloadJson = doc.getString("payload") ?: ""
                )
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("pull failed: ${e.message}", e)
        }
    }
}

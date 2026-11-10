package com.maakthi.prescriptionpharmacist.data.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Firebase Authentication (anonymous sign-in + ID tokens). When Firebase
 * is not configured the repository reports [isAvailable] = false and every
 * operation fails gracefully — no dummy users are ever fabricated.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseSupport: FirebaseSupport
) : AuthRepository {

    private val auth: FirebaseAuth?
        get() = if (firebaseSupport.isAvailable) {
            runCatching { FirebaseAuth.getInstance() }.getOrNull()
        } else {
            null
        }

    override fun isAvailable(): Boolean = firebaseSupport.isAvailable

    override val currentUser: Flow<AppUser?> =
        if (!firebaseSupport.isAvailable) {
            flowOf(null)
        } else {
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    trySend(firebaseAuth.currentUser?.toDomain())
                }
                val firebaseAuth = auth
                firebaseAuth?.addAuthStateListener(listener)
                awaitClose { firebaseAuth?.removeAuthStateListener(listener) }
            }
        }

    override suspend fun signInAnonymously(): AppResult<AppUser> {
        val firebaseAuth = auth
            ?: return AppResult.error(ErrorCode.NOT_CONFIGURED, "Firebase is not configured")
        return try {
            val result = Tasks.await(
                firebaseAuth.signInAnonymously(),
                30,
                TimeUnit.SECONDS
            )
            val user = result.user
                ?: return AppResult.error(ErrorCode.UNKNOWN, "No user returned")
            AppResult.success(user.toDomain())
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNKNOWN, cause = e)
        }
    }

    override suspend fun signOut() {
        auth?.signOut()
    }

    override suspend fun getIdToken(): String? {
        val user = auth?.currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).token
        } catch (e: Exception) {
            null
        }
    }

    private fun FirebaseUser.toDomain() = AppUser(
        uid = uid,
        isAnonymous = isAnonymous,
        displayName = displayName
    )
}

package com.maakthi.prescriptionpharmacist.data.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import com.maakthi.prescriptionpharmacist.domain.model.userRoleOf
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Firebase Authentication: email/password (with verification), Google,
 * password reset, account deletion, and role lookup from ID-token claims.
 *
 * When Firebase is not configured the repository reports [isAvailable] = false
 * and every operation fails gracefully — no dummy users are ever fabricated.
 * Error results carry the Firebase error code in `message` so the UI layer can
 * map it to a friendly [com.maakthi.prescriptionpharmacist.core.auth.AuthErrorType].
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
        val firebaseAuth = auth ?: return notConfigured()
        return awaitAuth { firebaseAuth.signInAnonymously() }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): AppResult<AppUser> {
        val firebaseAuth = auth ?: return notConfigured()
        return awaitAuth { firebaseAuth.signInWithEmailAndPassword(email, password) }
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String
    ): AppResult<AppUser> {
        val firebaseAuth = auth ?: return notConfigured()
        val result = awaitAuth { firebaseAuth.createUserWithEmailAndPassword(email, password) }
        // Send the verification link immediately after registration.
        if (result.isSuccess) {
            runCatching {
                Tasks.await(firebaseAuth.currentUser?.sendEmailVerification(), 20, TimeUnit.SECONDS)
            }
        }
        return result
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AppResult<AppUser> {
        val firebaseAuth = auth ?: return notConfigured()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return awaitAuth { firebaseAuth.signInWithCredential(credential) }
    }

    override suspend fun signOut() {
        auth?.signOut()
    }

    override suspend fun sendEmailVerification(): AppResult<Unit> {
        val user = auth?.currentUser
            ?: return AppResult.error(ErrorCode.UNAUTHORIZED, "NO_USER")
        return try {
            Tasks.await(user.sendEmailVerification(), 20, TimeUnit.SECONDS)
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNAUTHORIZED, e.friendlyCode(), e)
        }
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> {
        val firebaseAuth = auth ?: return notConfigured()
        return try {
            Tasks.await(firebaseAuth.sendPasswordResetEmail(email), 20, TimeUnit.SECONDS)
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNAUTHORIZED, e.friendlyCode(), e)
        }
    }

    override suspend fun reloadUser(): AppResult<AppUser?> {
        val user = auth?.currentUser
            ?: return AppResult.success(null)
        return try {
            Tasks.await(user.reload(), 20, TimeUnit.SECONDS)
            AppResult.success(auth?.currentUser?.toDomain())
        } catch (e: Exception) {
            AppResult.error(ErrorCode.NETWORK, e.friendlyCode(), e)
        }
    }

    override suspend fun deleteAccount(): AppResult<Unit> {
        val user = auth?.currentUser
            ?: return AppResult.error(ErrorCode.UNAUTHORIZED, "NO_USER")
        return try {
            Tasks.await(user.delete(), 30, TimeUnit.SECONDS)
            AppResult.success(Unit)
        } catch (e: Exception) {
            // FirebaseRecentLoginRequiredException surfaces as REQUIRES_RECENT_LOGIN.
            AppResult.error(ErrorCode.UNAUTHORIZED, e.friendlyCode(), e)
        }
    }

    override suspend fun fetchRole(): UserRole {
        val user = auth?.currentUser ?: return UserRole.USER
        return try {
            val result = Tasks.await(user.getIdToken(true), 15, TimeUnit.SECONDS)
            userRoleOf(result.claims?.get("role") as? String)
        } catch (e: Exception) {
            UserRole.USER
        }
    }

    override suspend fun getIdToken(): String? {
        val user = auth?.currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).token
        } catch (e: Exception) {
            null
        }
    }

    private fun notConfigured(): AppResult<Nothing> =
        AppResult.error(ErrorCode.NOT_CONFIGURED, "NOT_CONFIGURED")

    private fun awaitAuth(block: () -> com.google.android.gms.tasks.Task<AuthResult>): AppResult<AppUser> {
        return try {
            val result = Tasks.await(block(), 30, TimeUnit.SECONDS)
            val user = result.user
                ?: return AppResult.error(ErrorCode.UNKNOWN, "NO_USER")
            AppResult.success(user.toDomain())
        } catch (e: Exception) {
            AppResult.error(ErrorCode.UNAUTHORIZED, e.friendlyCode(), e)
        }
    }

    /** Firebase error code ("ERROR_…") when available, else the message. */
    private fun Exception.friendlyCode(): String =
        (this as? FirebaseAuthException)?.errorCode ?: (message ?: "UNKNOWN")

    private fun FirebaseUser.toDomain() = AppUser(
        uid = uid,
        isAnonymous = isAnonymous,
        displayName = displayName ?: email?.substringBefore('@'),
        email = email,
        isEmailVerified = isEmailVerified
    )
}

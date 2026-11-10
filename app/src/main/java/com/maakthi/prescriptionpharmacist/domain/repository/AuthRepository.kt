package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import kotlinx.coroutines.flow.Flow

/**
 * Optional Firebase Authentication. When Firebase is not configured for the
 * build, [isAvailable] returns false and the app runs in local-only mode.
 */
interface AuthRepository {
    fun isAvailable(): Boolean
    val currentUser: Flow<AppUser?>
    suspend fun signInAnonymously(): AppResult<AppUser>
    suspend fun signInWithEmail(email: String, password: String): AppResult<AppUser>
    suspend fun registerWithEmail(email: String, password: String): AppResult<AppUser>
    suspend fun signInWithGoogleIdToken(idToken: String): AppResult<AppUser>
    suspend fun signOut()
    /** Current Firebase ID token (JWT) used to call the secure backend. */
    suspend fun getIdToken(): String?
}

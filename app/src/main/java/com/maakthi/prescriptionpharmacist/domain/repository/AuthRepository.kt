package com.maakthi.prescriptionpharmacist.domain.repository

import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.AppUser
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun isAvailable(): Boolean
    val currentUser: Flow<AppUser?>
    suspend fun signInAnonymously(): AppResult<AppUser>
    suspend fun signInWithEmail(email: String, password: String): AppResult<AppUser>
    suspend fun registerWithEmail(email: String, password: String): AppResult<AppUser>
    suspend fun signInWithGoogleIdToken(idToken: String): AppResult<AppUser>
    suspend fun signOut()

    /** Sends (or resends) the email-verification link to the signed-in user. */
    suspend fun sendEmailVerification(): AppResult<Unit>

    /** Sends a password-reset email. */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    /** Reloads the user from Firebase (email-verification state, profile). */
    suspend fun reloadUser(): AppResult<AppUser?>

    /**
     * Deletes the signed-in account. Fails with `REQUIRES_RECENT_LOGIN` when
     * Firebase demands a fresh sign-in first.
     */
    suspend fun deleteAccount(): AppResult<Unit>

    /** Role from the Firebase ID-token custom claim (`role`). Default: USER. */
    suspend fun fetchRole(): UserRole

    /** Current Firebase ID token (JWT) used to call the secure backend. */
    suspend fun getIdToken(): String?
}

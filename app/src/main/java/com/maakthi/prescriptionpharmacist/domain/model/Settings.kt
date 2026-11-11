package com.maakthi.prescriptionpharmacist.domain.model

/** Application settings chosen by the user. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val backendUrl: String = "",
    val onboardingDone: Boolean = false,
    val mediaSyncEnabled: Boolean = false
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }

/**
 * Application roles. Enforced server-side through Firebase custom claims
 * (`role`) and Firestore security rules; the UI only reflects them.
 */
enum class UserRole { USER, PHARMACIST, ADMIN }

fun userRoleOf(raw: String?): UserRole = when (raw?.trim()?.lowercase()) {
    "admin" -> UserRole.ADMIN
    "pharmacist" -> UserRole.PHARMACIST
    else -> UserRole.USER
}

/** Signed-in user, when Firebase Authentication is configured. */
data class AppUser(
    val uid: String,
    val isAnonymous: Boolean,
    val displayName: String? = null,
    val email: String? = null,
    val isEmailVerified: Boolean = true,
    val role: UserRole = UserRole.USER
)

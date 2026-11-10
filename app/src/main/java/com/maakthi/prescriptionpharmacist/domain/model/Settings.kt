package com.maakthi.prescriptionpharmacist.domain.model

/** Application settings chosen by the user. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val backendUrl: String = "",
    val onboardingDone: Boolean = false
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }

/** Signed-in user, when Firebase Authentication is configured. */
data class AppUser(
    val uid: String,
    val isAnonymous: Boolean,
    val displayName: String? = null
)

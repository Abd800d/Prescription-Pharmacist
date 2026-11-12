package com.maakthi.prescriptionpharmacist.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Firebase availability check. Firebase features are active only
 * when the developer provides `app/google-services.json`; otherwise the app
 * runs fully in local mode.
 */
@Singleton
class FirebaseSupport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isAvailable: Boolean by lazy {
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
}

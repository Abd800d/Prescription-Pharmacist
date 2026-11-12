package com.maakthi.prescriptionpharmacist.core.privacy

/**
 * Account & data deletion policy — the single source of truth for what is
 * stored where and what "delete my data" removes. Used by the privacy screen
 * and by PRIVACY_POLICY.md so the two can never drift apart.
 *
 * Policy:
 * - Data is stored in two places: this device (local database, media files,
 *   settings) and — only if the user enabled sync/backup — their private
 *   Firebase space (users/{uid}/…).
 * - "Delete data on this device" wipes ALL local categories immediately.
 * - Cloud copies are deleted by "delete cloud data" (when signed in) or by
 *   request to the support email; account deletion is confirmed within 30
 *   days.
 * - Backups the user exported (.ppbak files) are under the user's own control.
 */
object DataDeletionPolicy {

    /** Everything stored locally that "delete my data" removes. */
    val LOCAL_CATEGORIES: List<String> = listOf(
        "local.database",          // drugs, prescriptions, medications, audit log
        "local.media",             // prescription + product images (encrypted at rest)
        "local.settings",          // preferences, backend URL, sync clocks
        "local.device_key",        // image-encryption key envelope
        "local.backup_vault",      // encrypted backup passphrase
        "local.cache"              // thumbnails, temp files, support attachments
    )

    /** Copies that can live in the user's private cloud space. */
    val CLOUD_CATEGORIES: List<String> = listOf(
        "cloud.sync",              // users/{uid}/sync (drugs + prescriptions)
        "cloud.media",             // users/{uid}/media (images)
        "cloud.backups",           // users/{uid}/backups (encrypted .ppbak)
        "cloud.account"            // Firebase Auth account
    )

    /** Data that leaves the app's control (exported by the user). */
    val USER_CONTROLLED: List<String> = listOf(
        "exported.ppbak",          // encrypted backups the user saved elsewhere
        "exported.xlsx"            // exports the user saved elsewhere
    )

    const val ACCOUNT_DELETION_WINDOW_DAYS = 30

    fun localWipeCoversAllLocalData(deleted: Collection<String>): Boolean =
        deleted.containsAll(LOCAL_CATEGORIES)
}

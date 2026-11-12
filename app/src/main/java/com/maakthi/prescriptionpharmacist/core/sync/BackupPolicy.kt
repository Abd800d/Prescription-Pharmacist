package com.maakthi.prescriptionpharmacist.core.sync

/**
 * When to run the automatic (encrypted) backup. Pure and unit-tested.
 */
object BackupPolicy {

    /** At most one automatic backup per day. */
    const val AUTO_BACKUP_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

    /**
     * Automatic backup runs when it has never happened, when the daily
     * interval passed, or when a successful sync produced new data to
     * protect.
     */
    fun shouldAutoBackup(
        autoBackupEnabled: Boolean,
        now: Long,
        lastBackupAt: Long,
        lastSyncAt: Long
    ): Boolean {
        if (!autoBackupEnabled) return false
        if (lastBackupAt <= 0) return true
        if (now - lastBackupAt >= AUTO_BACKUP_INTERVAL_MILLIS) return true
        return lastSyncAt > lastBackupAt
    }
}

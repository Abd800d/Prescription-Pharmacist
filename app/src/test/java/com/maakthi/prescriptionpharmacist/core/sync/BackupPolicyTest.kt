package com.maakthi.prescriptionpharmacist.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Automatic encrypted-backup timing policy. */
class BackupPolicyTest {

    @Test
    fun `disabled auto backup never triggers`() {
        assertFalse(
            BackupPolicy.shouldAutoBackup(
                autoBackupEnabled = false, now = 100, lastBackupAt = 0, lastSyncAt = 50
            )
        )
    }

    @Test
    fun `first backup runs immediately`() {
        assertTrue(
            BackupPolicy.shouldAutoBackup(
                autoBackupEnabled = true, now = 100, lastBackupAt = 0, lastSyncAt = 0
            )
        )
    }

    @Test
    fun `daily interval triggers a backup`() {
        val lastBackup = 1_000L
        assertTrue(
            BackupPolicy.shouldAutoBackup(
                autoBackupEnabled = true,
                now = lastBackup + BackupPolicy.AUTO_BACKUP_INTERVAL_MILLIS,
                lastBackupAt = lastBackup,
                lastSyncAt = lastBackup
            )
        )
        assertFalse(
            BackupPolicy.shouldAutoBackup(
                autoBackupEnabled = true,
                now = lastBackup + BackupPolicy.AUTO_BACKUP_INTERVAL_MILLIS - 1,
                lastBackupAt = lastBackup,
                lastSyncAt = lastBackup
            )
        )
    }

    @Test
    fun `a successful sync after the last backup triggers a backup`() {
        assertTrue(
            BackupPolicy.shouldAutoBackup(
                autoBackupEnabled = true, now = 200, lastBackupAt = 150, lastSyncAt = 180
            )
        )
    }
}

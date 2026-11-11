package com.maakthi.prescriptionpharmacist.core.sync

import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict

/**
 * Conflict resolution: a conflict is ONLY resolved by an explicit user
 * choice. The engine never picks a winner automatically — both versions are
 * preserved until the user decides.
 */
object ConflictResolver {

    data class Resolution(
        val winningJson: String,
        val winningUpdatedAt: Long
    )

    /** Applies the user's explicit choice; the winner's clock moves to now. */
    fun resolve(conflict: SyncConflict, choice: ConflictChoice, now: Long): Resolution = when (choice) {
        ConflictChoice.KEEP_LOCAL -> Resolution(conflict.localJson, now)
        ConflictChoice.KEEP_REMOTE -> Resolution(conflict.remoteJson, now)
    }

    /** Both versions must survive until the user chooses. */
    fun preservesBothVersions(conflict: SyncConflict): Boolean =
        conflict.localJson.isNotBlank() && conflict.remoteJson.isNotBlank()
}

package com.maakthi.prescriptionpharmacist.core.media

import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.UserRole

/**
 * Viewing/deleting permission policy for saved images.
 *
 * Images are private to their owner. Admins may view and delete any asset;
 * nobody else may access someone else's images. The same policy is enforced
 * in the Storage rules (`storage.rules`) on the server side.
 */
object MediaAccess {

    fun canView(asset: MediaAsset, viewerUid: String, viewerRole: UserRole): Boolean =
        viewerUid.isNotBlank() &&
            (viewerUid == asset.ownerId || viewerRole == UserRole.ADMIN)

    fun canDelete(asset: MediaAsset, viewerUid: String, viewerRole: UserRole): Boolean =
        canView(asset, viewerUid, viewerRole)

    /** Only the owner (or an admin) may sync their own images out of the device. */
    fun canSync(asset: MediaAsset, viewerUid: String, viewerRole: UserRole): Boolean =
        canView(asset, viewerUid, viewerRole)

    /** The remote object path — always scoped under the owner's private space. */
    fun remotePathFor(ownerUid: String, kind: String, id: String): String =
        "users/$ownerUid/media/$kind/$id.bin"
}

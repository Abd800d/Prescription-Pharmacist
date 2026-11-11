package com.maakthi.prescriptionpharmacist.core.media

import com.maakthi.prescriptionpharmacist.domain.model.MediaAsset
import com.maakthi.prescriptionpharmacist.domain.model.MediaKind
import com.maakthi.prescriptionpharmacist.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Viewing/deleting permissions: owner or admin only, never public. */
class MediaAccessTest {

    private fun asset(ownerId: String = "user-1") = MediaAsset(
        id = "a1",
        kind = MediaKind.PRESCRIPTION_IMAGE,
        ownerId = ownerId,
        ownerName = "Ahmed",
        capturedAt = 1L,
        localPath = "/data/a1.bin"
    )

    @Test
    fun `owner can view and delete`() {
        assertTrue(MediaAccess.canView(asset(), "user-1", UserRole.USER))
        assertTrue(MediaAccess.canDelete(asset(), "user-1", UserRole.USER))
        assertTrue(MediaAccess.canSync(asset(), "user-1", UserRole.USER))
    }

    @Test
    fun `admin can view and delete`() {
        assertTrue(MediaAccess.canView(asset(), "admin-9", UserRole.ADMIN))
        assertTrue(MediaAccess.canDelete(asset(), "admin-9", UserRole.ADMIN))
    }

    @Test
    fun `other users are denied`() {
        assertFalse(MediaAccess.canView(asset(), "user-2", UserRole.USER))
        assertFalse(MediaAccess.canView(asset(), "user-2", UserRole.PHARMACIST))
        assertFalse(MediaAccess.canDelete(asset(), "user-2", UserRole.USER))
    }

    @Test
    fun `anonymous empty viewers are denied`() {
        assertFalse(MediaAccess.canView(asset(), "", UserRole.USER))
    }

    @Test
    fun `remote paths are scoped under the owner`() {
        assertEquals(
            "users/user-1/media/prescription_image/a1.bin",
            MediaAccess.remotePathFor("user-1", "prescription_image", "a1")
        )
    }
}

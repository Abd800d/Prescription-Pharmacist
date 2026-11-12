package com.maakthi.prescriptionpharmacist.core.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google Play consent gating: no image processing before explicit agreement. */
class ConsentPolicyTest {

    @Test
    fun `consent is required until the user agrees`() {
        assertTrue(ConsentPolicy.isConsentRequired(0L))
        assertTrue(ConsentPolicy.isConsentRequired(-5L))
        assertFalse(ConsentPolicy.isConsentRequired(1L))
    }

    @Test
    fun `images cannot be processed before consent`() {
        assertFalse(ConsentPolicy.canProcessImages(0L))
        assertTrue(ConsentPolicy.canProcessImages(1_700_000_000_000L))
    }

    @Test
    fun `consent version is pinned`() {
        assertTrue(ConsentPolicy.CONSENT_VERSION >= 1)
    }
}

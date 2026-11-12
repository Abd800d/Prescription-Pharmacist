package com.maakthi.prescriptionpharmacist.core.privacy

/**
 * Data-processing consent policy (images + health data).
 *
 * Google Play requirement: the user must explicitly agree to image/data
 * processing BEFORE any prescription image leaves the device for analysis.
 * The consent moment is stored as a timestamp; re-installations ask again.
 */
object ConsentPolicy {

    /** Bump when the consent text changes materially. */
    const val CONSENT_VERSION = 1

    fun isConsentRequired(grantedAt: Long): Boolean = grantedAt <= 0L

    /** Image analysis (upload to the secure backend) is allowed only after consent. */
    fun canProcessImages(grantedAt: Long): Boolean = !isConsentRequired(grantedAt)
}

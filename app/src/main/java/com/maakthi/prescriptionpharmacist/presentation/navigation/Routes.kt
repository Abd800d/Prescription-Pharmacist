package com.maakthi.prescriptionpharmacist.presentation.navigation

import android.net.Uri

object Routes {
    // Top level
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DRUG_SEARCH = "drug_search"
    const val SAVED = "saved"
    const val PRODUCTS = "products"
    const val SETTINGS = "settings"

    // Auth
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val GOOGLE_SIGN_IN = "google_sign_in"

    // Capture flow
    const val CAPTURE = "capture"
    const val GALLERY = "gallery"
    const val CROP = "crop/{imagePath}"
    fun crop(imagePath: String) = "crop/${Uri.encode(imagePath)}"
    const val PREVIEW = "preview/{imagePath}"
    fun preview(imagePath: String) = "preview/${Uri.encode(imagePath)}"

    // Review flow (nested graph sharing one ViewModel)
    const val REVIEW_GRAPH = "review/{imagePath}"
    const val REVIEW_ANALYSIS = "review/analysis"
    const val REVIEW_EXTRACTED = "review/extracted"
    const val REVIEW_CONFIRM = "review/confirm"
    fun reviewGraph(imagePath: String) = "review/${Uri.encode(imagePath)}"

    // Drugs
    const val DRUG_ADD = "drug_add"
    const val DRUG_EDIT = "drug_edit/{drugId}"
    const val DRUG_DETAILS = "drug_details/{drugId}"
    fun drugEdit(drugId: String) = "drug_edit/${Uri.encode(drugId)}"
    fun drugDetails(drugId: String): String = "drug_details/${Uri.encode(drugId)}"

    // Prescriptions
    const val DETAIL = "detail/{prescriptionId}"
    fun detail(prescriptionId: String): String = "detail/${Uri.encode(prescriptionId)}"

    // Misc
    const val TRANSFER = "transfer"
    const val ASSISTANT = "assistant"
    const val LANGUAGE_SETTINGS = "language_settings"
    const val BACKUP = "backup"
    const val SUPPORT = "support"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
}

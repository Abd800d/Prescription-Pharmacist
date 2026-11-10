package com.maakthi.prescriptionpharmacist.presentation.navigation

import android.net.Uri

object Routes {
    const val CAPTURE = "capture"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val REVIEW = "review/{imagePath}"
    const val DETAIL = "detail/{prescriptionId}"

    fun review(imagePath: String): String = "review/${Uri.encode(imagePath)}"
    fun detail(prescriptionId: String): String = "detail/${Uri.encode(prescriptionId)}"
}

package com.maakthi.prescriptionpharmacist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenFdaResponse(
    val results: List<OpenFdaDrug> = emptyList()
)

@Serializable
data class OpenFdaDrug(
    val openfda: OpenFdaInfo? = null,
    @SerialName("indications_and_usage") val indicationsAndUsage: List<String> = emptyList(),
    @SerialName("dosage_and_administration") val dosageAndAdministration: List<String> = emptyList(),
    val description: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class OpenFdaInfo(
    @SerialName("brand_name") val brandName: List<String> = emptyList(),
    @SerialName("generic_name") val genericName: List<String> = emptyList(),
    @SerialName("manufacturer_name") val manufacturerName: List<String> = emptyList()
)

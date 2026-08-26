package com.pgoorts.tripplanner.pkpass

import kotlinx.serialization.Serializable

@Serializable
data class PkpassField(
    val key: String,
    val label: String,
    val value: String
)

@Serializable
data class PkpassContent(
    val storagePath: String,
    val originalFileName: String,
    val passType: String,
    val organizationName: String,
    val description: String,
    val serialNumber: String,
    val barcodeMessage: String,
    val barcodeFormat: String,
    val fields: List<PkpassField>
)

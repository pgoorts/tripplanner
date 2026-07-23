package com.pgoorts.tripplanner.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItem(
    val text: String,
    val isChecked: Boolean = false
)

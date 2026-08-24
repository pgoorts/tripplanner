package com.pgoorts.tripplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey
    val id: String,
    val destination: String,
    val startDate: String,           // ISO-8601: YYYY-MM-DD
    val endDate: String,             // ISO-8601: YYYY-MM-DD
    val collaborators: String,       // JSON object: {"email": "ROLE"}
    val defaultTimezone: String? = null, // IANA Timezone ID; per-trip override of the global default
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null
)

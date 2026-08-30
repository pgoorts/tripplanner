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
    val coverPhotoStoragePath: String? = null, // Synced; Firebase Storage path, e.g. "trips/{id}/cover.jpg"
    val coverPhotoSource: String? = null,      // Synced; one of AUTO_PLACES/AUTO_UNSPLASH/USER, or null
    val localCoverPhotoPath: String? = null,   // Local-only; staged image awaiting Storage upload
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null
)

package com.pgoorts.tripplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("eventId")]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val tripId: String,
    val eventId: String? = null,     // Nullable: reminder may belong to a trip or a specific event
    val text: String,
    val date: String,                // ISO-8601: YYYY-MM-DD
    val time: String,                // ISO-8601: HH:MM
    val isTriggeredLocal: Boolean = false, // Local only, not synced to cloud
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null
)

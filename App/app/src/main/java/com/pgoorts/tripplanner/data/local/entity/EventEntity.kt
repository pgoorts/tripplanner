package com.pgoorts.tripplanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val tripId: String,
    val title: String,
    val category: EventCategory,
    val location: String? = null,
    @ColumnInfo(name = "timezone")
    val startTimezone: String,       // IANA Timezone ID e.g. "Europe/Rome"; physical column still "timezone"
    val endTimezone: String? = null, // IANA Timezone ID; null reads as "same as startTimezone" (pre-Phase-4 events)
    val startDate: String,           // ISO-8601: YYYY-MM-DD
    val startTime: String? = null,   // ISO-8601: HH:MM (null = all-day)
    val endDate: String,             // ISO-8601: YYYY-MM-DD
    val endTime: String? = null,     // ISO-8601: HH:MM
    val description: String? = null,
    val flightNumber: String? = null,            // Flight category only
    val departureAirportCode: String? = null,     // Flight category only
    val arrivalAirportCode: String? = null,       // Flight category only
    val bookingNumber: String? = null,            // Lodging category only
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null
)

package com.pgoorts.tripplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
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
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val tripId: String,
    val eventId: String? = null,     // Nullable: note may belong to a trip or a specific event
    val title: String,
    val type: NoteType,
    val content: String,             // Freeform text or JSON array for CHECKLIST
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null,
    val localAttachmentPath: String? = null // Local-only; PKPASS/FILE notes only, cleared once uploaded
)

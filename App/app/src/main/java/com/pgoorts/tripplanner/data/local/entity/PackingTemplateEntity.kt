package com.pgoorts.tripplanner.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "packing_templates",
    indices = [Index("ownerEmail")]
)
data class PackingTemplateEntity(
    @PrimaryKey
    val id: String,
    val ownerEmail: String,          // Google Sign-In email of the owner
    val title: String,
    val items: String,               // JSON array of strings: ["Passport", "Toothbrush"]
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING_INSERT,
    val lastSyncedAt: Long? = null
)

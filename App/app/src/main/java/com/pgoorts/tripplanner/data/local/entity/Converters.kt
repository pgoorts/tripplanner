package com.pgoorts.tripplanner.data.local.entity

import androidx.room.TypeConverter

class Converters {

    // SyncState
    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    // EventCategory
    @TypeConverter
    fun fromEventCategory(value: EventCategory): String = value.name

    @TypeConverter
    fun toEventCategory(value: String): EventCategory = EventCategory.valueOf(value)

    // NoteType
    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)
}

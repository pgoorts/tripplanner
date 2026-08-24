package com.pgoorts.tripplanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pgoorts.tripplanner.data.local.dao.EventDao
import com.pgoorts.tripplanner.data.local.dao.NoteDao
import com.pgoorts.tripplanner.data.local.dao.PackingTemplateDao
import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.dao.TripDao
import com.pgoorts.tripplanner.data.local.entity.Converters
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        EventEntity::class,
        NoteEntity::class,
        ReminderEntity::class,
        PackingTemplateEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun packingTemplateDao(): PackingTemplateDao
}

// Phase 3: adds trip/event settings + structured event fields + the PKPASS note attachment path.
// Every added column is nullable, so existing rows are valid as-is with NULL in the new columns.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN defaultTimezone TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN flightNumber TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN departureAirportCode TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN arrivalAirportCode TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN bookingNumber TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN localAttachmentPath TEXT")
    }
}

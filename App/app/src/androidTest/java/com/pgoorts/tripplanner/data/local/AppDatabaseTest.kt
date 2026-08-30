package com.pgoorts.tripplanner.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pgoorts.tripplanner.data.local.dao.EventDao
import com.pgoorts.tripplanner.data.local.dao.NoteDao
import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.dao.TripDao
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.SyncState
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var tripDao: TripDao
    private lateinit var eventDao: EventDao
    private lateinit var noteDao: NoteDao
    private lateinit var reminderDao: ReminderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        tripDao = db.tripDao()
        eventDao = db.eventDao()
        noteDao = db.noteDao()
        reminderDao = db.reminderDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    // ---- Trip Tests ----

    @Test
    fun insertAndQueryTrip() = runTest {
        val trip = buildTrip("trip-1")
        tripDao.insertTrip(trip)

        val trips = tripDao.getAllTrips().first()
        assertEquals(1, trips.size)
        assertEquals("trip-1", trips[0].id)
        assertEquals("Rome, Italy", trips[0].destination)
    }

    @Test
    fun updateTrip() = runTest {
        val trip = buildTrip("trip-2")
        tripDao.insertTrip(trip)

        val updated = trip.copy(destination = "Paris, France", syncState = SyncState.PENDING_UPDATE)
        tripDao.updateTrip(updated)

        val result = tripDao.getTripById("trip-2").first()
        assertEquals("Paris, France", result?.destination)
        assertEquals(SyncState.PENDING_UPDATE, result?.syncState)
    }

    @Test
    fun deleteTripById() = runTest {
        val trip = buildTrip("trip-3")
        tripDao.insertTrip(trip)
        tripDao.deleteTripById("trip-3")

        val result = tripDao.getTripById("trip-3").first()
        assertNull(result)
    }

    @Test
    fun pendingSyncTrips() = runTest {
        tripDao.insertTrip(buildTrip("trip-sync-1", SyncState.PENDING_INSERT))
        tripDao.insertTrip(buildTrip("trip-sync-2", SyncState.SYNCED))
        tripDao.insertTrip(buildTrip("trip-sync-3", SyncState.PENDING_UPDATE))

        val pending = tripDao.getPendingSyncTrips()
        assertEquals(2, pending.size)
        assertTrue(pending.none { it.syncState == SyncState.SYNCED })
    }

    // ---- Event Tests ----

    @Test
    fun insertAndQueryEvents() = runTest {
        val trip = buildTrip("trip-ev-1")
        tripDao.insertTrip(trip)

        val event1 = buildEvent("event-1", "trip-ev-1", startTime = null)      // all-day
        val event2 = buildEvent("event-2", "trip-ev-1", startTime = "14:00")   // timed
        eventDao.insertEvent(event1)
        eventDao.insertEvent(event2)

        val events = eventDao.getEventsByTripId("trip-ev-1").first()
        assertEquals(2, events.size)
        // All-day (null time) should come first due to NULLS FIRST ordering
        assertNull(events[0].startTime)
        assertNotNull(events[1].startTime)
    }

    @Test
    fun cascadeDeleteEventsOnTripDelete() = runTest {
        val trip = buildTrip("trip-cascade-1")
        tripDao.insertTrip(trip)
        eventDao.insertEvent(buildEvent("event-cascade-1", "trip-cascade-1"))

        tripDao.deleteTripById("trip-cascade-1")

        val events = eventDao.getEventsByTripId("trip-cascade-1").first()
        assertTrue(events.isEmpty())
    }

    // ---- Note Tests ----

    @Test
    fun insertAndQueryNotes() = runTest {
        val trip = buildTrip("trip-note-1")
        tripDao.insertTrip(trip)

        val note = NoteEntity(
            id = "note-1",
            tripId = "trip-note-1",
            title = "Packing List",
            type = NoteType.CHECKLIST,
            content = """[{"text":"Passport","checked":false}]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNote(note)

        val notes = noteDao.getNotesByTripId("trip-note-1").first()
        assertEquals(1, notes.size)
        assertEquals("Packing List", notes[0].title)
        assertEquals(NoteType.CHECKLIST, notes[0].type)
    }

    @Test
    fun updateNote() = runTest {
        val trip = buildTrip("trip-note-2")
        tripDao.insertTrip(trip)

        val note = NoteEntity(
            id = "note-2",
            tripId = "trip-note-2",
            title = "Old Title",
            type = NoteType.TEXT_BLOCK,
            content = "Some text",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNote(note)

        val updatedNote = note.copy(title = "New Title", syncState = SyncState.PENDING_UPDATE)
        noteDao.updateNote(updatedNote)

        val result = noteDao.getNoteById("note-2").first()
        assertEquals("New Title", result?.title)
    }

    // ---- Reminder Tests ----

    @Test
    fun insertAndQueryReminders() = runTest {
        val trip = buildTrip("trip-rem-1")
        tripDao.insertTrip(trip)

        val reminder = ReminderEntity(
            id = "reminder-1",
            tripId = "trip-rem-1",
            text = "Book taxi to airport",
            date = "2026-06-01",
            time = "07:00",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        reminderDao.insertReminder(reminder)

        val reminders = reminderDao.getRemindersByTripId("trip-rem-1").first()
        assertEquals(1, reminders.size)
        assertEquals("Book taxi to airport", reminders[0].text)
    }

    @Test
    fun cascadeDeleteRemindersOnTripDelete() = runTest {
        val trip = buildTrip("trip-cascade-rem-1")
        tripDao.insertTrip(trip)

        val reminder = ReminderEntity(
            id = "reminder-cascade-1",
            tripId = "trip-cascade-rem-1",
            text = "Test",
            date = "2026-01-01",
            time = "09:00",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        reminderDao.insertReminder(reminder)

        tripDao.deleteTripById("trip-cascade-rem-1")

        val reminders = reminderDao.getRemindersByTripId("trip-cascade-rem-1").first()
        assertTrue(reminders.isEmpty())
    }

    // ---- Helpers ----

    private fun buildTrip(
        id: String,
        syncState: SyncState = SyncState.PENDING_INSERT
    ) = TripEntity(
        id = id,
        destination = "Rome, Italy",
        startDate = "2026-06-01",
        endDate = "2026-06-10",
        collaborators = """{"owner@example.com":"CO_OWNER"}""",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        syncState = syncState
    )

    private fun buildEvent(
        id: String,
        tripId: String,
        startTime: String? = "10:00"
    ) = EventEntity(
        id = id,
        tripId = tripId,
        title = "Test Event",
        category = EventCategory.ACTIVITY,
        startTimezone = "Europe/Rome",
        startDate = "2026-06-03",
        startTime = startTime,
        endDate = "2026-06-03",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

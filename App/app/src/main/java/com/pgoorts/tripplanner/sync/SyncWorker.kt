package com.pgoorts.tripplanner.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.dao.TripDao
import com.pgoorts.tripplanner.data.local.dao.EventDao
import com.pgoorts.tripplanner.data.local.dao.NoteDao
import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.dao.PackingTemplateDao
import com.pgoorts.tripplanner.data.local.entity.*
import com.pgoorts.tripplanner.data.repository.PreferencesRepository
import com.pgoorts.tripplanner.pkpass.PkpassParser
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        /** Input data key: true for the manual "Sync now" run, absent/false for the periodic job. */
        const val KEY_IS_MANUAL = "is_manual_sync"
    }

    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun tripDao(): TripDao
        fun eventDao(): EventDao
        fun noteDao(): NoteDao
        fun reminderDao(): ReminderDao
        fun packingTemplateDao(): PackingTemplateDao
        fun userSessionManager(): UserSessionManager
        fun preferencesRepository(): PreferencesRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPoints.get(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        
        val tripDao = entryPoint.tripDao()
        val eventDao = entryPoint.eventDao()
        val noteDao = entryPoint.noteDao()
        val reminderDao = entryPoint.reminderDao()
        val packingTemplateDao = entryPoint.packingTemplateDao()
        val userSessionManager = entryPoint.userSessionManager()
        val preferencesRepository = entryPoint.preferencesRepository()

        val currentUserEmail = userSessionManager.userEmail ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        try {
            // 1. Upload Flow (Local -> Cloud)
            performUploadFlow(db, tripDao, eventDao, noteDao, reminderDao, packingTemplateDao)

            // 2. Download Flow (Cloud -> Local) & Conflict Resolution
            performDownloadFlow(db, currentUserEmail, tripDao, eventDao, noteDao, reminderDao, packingTemplateDao)

            // Both phases completed without error — this is a full successful sync pass.
            preferencesRepository.setLastSuccessfulSyncAt(System.currentTimeMillis())

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // The periodic job retries silently in the background; the manual "Sync now" run is
            // user-visible and waiting on this attempt, so it fails fast instead of looping.
            return if (inputData.getBoolean(KEY_IS_MANUAL, false)) Result.failure() else Result.retry()
        }
    }

    private suspend fun performUploadFlow(
        db: FirebaseFirestore,
        tripDao: TripDao,
        eventDao: EventDao,
        noteDao: NoteDao,
        reminderDao: ReminderDao,
        packingTemplateDao: PackingTemplateDao
    ) {
        // --- Upload Trips ---
        val pendingTrips = tripDao.getPendingSyncTrips()
        for (trip in pendingTrips) {
            when (trip.syncState) {
                SyncState.PENDING_INSERT, SyncState.PENDING_UPDATE -> {
                    val collaboratorsMap = try {
                        Json.decodeFromString<Map<String, String>>(trip.collaborators)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                    val data = mapOf(
                        "destination" to trip.destination,
                        "startDate" to trip.startDate,
                        "endDate" to trip.endDate,
                        "collaborators" to collaboratorsMap,
                        "createdAt" to trip.createdAt,
                        "updatedAt" to trip.updatedAt
                    )
                    db.collection("trips").document(trip.id).set(data).await()
                    tripDao.insertTrip(trip.copy(syncState = SyncState.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
                SyncState.PENDING_DELETE -> {
                    db.collection("trips").document(trip.id).delete().await()
                    tripDao.deleteTrip(trip)
                }
                else -> {}
            }
        }

        // --- Upload Events ---
        val pendingEvents = eventDao.getPendingSyncEvents()
        for (event in pendingEvents) {
            when (event.syncState) {
                SyncState.PENDING_INSERT, SyncState.PENDING_UPDATE -> {
                    val data = mapOf(
                        "tripId" to event.tripId,
                        "title" to event.title,
                        "category" to event.category.name,
                        "location" to event.location,
                        "timezone" to event.startTimezone,
                        "endTimezone" to event.endTimezone,
                        "startDate" to event.startDate,
                        "startTime" to event.startTime,
                        "endDate" to event.endDate,
                        "endTime" to event.endTime,
                        "description" to event.description,
                        "flightNumber" to event.flightNumber,
                        "departureAirportCode" to event.departureAirportCode,
                        "arrivalAirportCode" to event.arrivalAirportCode,
                        "bookingNumber" to event.bookingNumber,
                        "createdAt" to event.createdAt,
                        "updatedAt" to event.updatedAt
                    )
                    db.collection("trips").document(event.tripId)
                        .collection("events").document(event.id)
                        .set(data).await()
                    eventDao.insertEvent(event.copy(syncState = SyncState.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
                SyncState.PENDING_DELETE -> {
                    db.collection("trips").document(event.tripId)
                        .collection("events").document(event.id)
                        .delete().await()
                    eventDao.deleteEvent(event)
                }
                else -> {}
            }
        }

        // --- Upload Notes ---
        val storage = FirebaseStorage.getInstance()
        val pendingNotes = noteDao.getPendingSyncNotes()
        for (note in pendingNotes) {
            when (note.syncState) {
                SyncState.PENDING_INSERT, SyncState.PENDING_UPDATE -> {
                    // A Pkpass note's raw file uploads to Storage independently of the Note
                    // document below — a transient Storage failure here doesn't block the Note
                    // document from syncing, since `content` already carries everything needed
                    // to render (per datastructure.txt §5). It's only marked SYNCED, and its
                    // local cached copy cleared, once BOTH have succeeded.
                    val localAttachmentPath = note.localAttachmentPath
                    var attachmentSynced = true
                    if (note.type == NoteType.PKPASS && localAttachmentPath != null) {
                        val storagePath = PkpassParser.extractStoragePath(note.content)
                        attachmentSynced = if (storagePath != null) {
                            try {
                                storage.reference.child(storagePath)
                                    .putFile(Uri.fromFile(File(localAttachmentPath)))
                                    .await()
                                true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                        } else false
                    }

                    val data = mapOf(
                        "tripId" to note.tripId,
                        "eventId" to note.eventId,
                        "title" to note.title,
                        "type" to note.type.name,
                        "content" to note.content,
                        "createdAt" to note.createdAt,
                        "updatedAt" to note.updatedAt
                    )
                    db.collection("trips").document(note.tripId)
                        .collection("notes").document(note.id)
                        .set(data).await()

                    if (attachmentSynced) {
                        if (note.type == NoteType.PKPASS && localAttachmentPath != null) {
                            File(localAttachmentPath).delete()
                        }
                        noteDao.insertNote(
                            note.copy(
                                syncState = SyncState.SYNCED,
                                lastSyncedAt = System.currentTimeMillis(),
                                localAttachmentPath = null
                            )
                        )
                    }
                    // else: leave syncState as-is; the next sync pass retries the Storage upload
                    // (the Firestore write above is idempotent, so re-running it is harmless).
                }
                SyncState.PENDING_DELETE -> {
                    db.collection("trips").document(note.tripId)
                        .collection("notes").document(note.id)
                        .delete().await()
                    if (note.type == NoteType.PKPASS) {
                        PkpassParser.extractStoragePath(note.content)?.let { storagePath ->
                            try {
                                storage.reference.child(storagePath).delete().await()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    noteDao.deleteNote(note)
                }
                else -> {}
            }
        }

        // --- Upload Reminders ---
        val pendingReminders = reminderDao.getPendingSyncReminders()
        for (reminder in pendingReminders) {
            when (reminder.syncState) {
                SyncState.PENDING_INSERT, SyncState.PENDING_UPDATE -> {
                    val data = mapOf(
                        "tripId" to reminder.tripId,
                        "eventId" to reminder.eventId,
                        "text" to reminder.text,
                        "date" to reminder.date,
                        "time" to reminder.time,
                        "createdAt" to reminder.createdAt,
                        "updatedAt" to reminder.updatedAt
                    )
                    db.collection("trips").document(reminder.tripId)
                        .collection("reminders").document(reminder.id)
                        .set(data).await()
                    reminderDao.insertReminder(reminder.copy(syncState = SyncState.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
                SyncState.PENDING_DELETE -> {
                    db.collection("trips").document(reminder.tripId)
                        .collection("reminders").document(reminder.id)
                        .delete().await()
                    reminderDao.deleteReminder(reminder)
                }
                else -> {}
            }
        }

        // --- Upload Packing Templates ---
        val pendingTemplates = packingTemplateDao.getPendingSyncTemplates()
        for (template in pendingTemplates) {
            when (template.syncState) {
                SyncState.PENDING_INSERT, SyncState.PENDING_UPDATE -> {
                    val itemsList = try {
                        Json.decodeFromString<List<String>>(template.items)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val data = mapOf(
                        "ownerEmail" to template.ownerEmail,
                        "title" to template.title,
                        "items" to itemsList,
                        "createdAt" to template.createdAt,
                        "updatedAt" to template.updatedAt
                    )
                    db.collection("packing_templates").document(template.id)
                        .set(data).await()
                    packingTemplateDao.insertTemplate(template.copy(syncState = SyncState.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                }
                SyncState.PENDING_DELETE -> {
                    db.collection("packing_templates").document(template.id).delete().await()
                    packingTemplateDao.deleteTemplate(template)
                }
                else -> {}
            }
        }
    }

    private suspend fun performDownloadFlow(
        db: FirebaseFirestore,
        currentUserEmail: String,
        tripDao: TripDao,
        eventDao: EventDao,
        noteDao: NoteDao,
        reminderDao: ReminderDao,
        packingTemplateDao: PackingTemplateDao
    ) {
        // 1. Fetch remote trips for this user
        // FieldPath.of(...) is required here (rather than a dotted string) so an email containing
        // "." is treated as one literal map key, not parsed into nested field segments.
        val remoteTripsSnapshot = db.collection("trips")
            .whereNotEqualTo(
                com.google.firebase.firestore.FieldPath.of("collaborators", currentUserEmail),
                null
            )
            .get()
            .await()

        val remoteTripIds = mutableSetOf<String>()

        for (tripDoc in remoteTripsSnapshot.documents) {
            val tripId = tripDoc.id
            remoteTripIds.add(tripId)

            val remoteDestination = tripDoc.getString("destination") ?: ""
            val remoteStartDate = tripDoc.getString("startDate") ?: ""
            val remoteEndDate = tripDoc.getString("endDate") ?: ""
            val remoteCollaboratorsMap = tripDoc.get("collaborators") as? Map<*, *> ?: emptyMap<Any, Any>()
            val remoteCollaborators = remoteCollaboratorsMap.entries.associate { 
                it.key.toString() to it.value.toString() 
            }
            val remoteCreatedAt = tripDoc.getLong("createdAt") ?: 0L
            val remoteUpdatedAt = tripDoc.getLong("updatedAt") ?: 0L

            val localTrip = tripDao.getTripById(tripId).first()
            val collaboratorsJson = Json.encodeToString(remoteCollaborators)

            if (localTrip == null) {
                val newTrip = TripEntity(
                    id = tripId,
                    destination = remoteDestination,
                    startDate = remoteStartDate,
                    endDate = remoteEndDate,
                    collaborators = collaboratorsJson,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    syncState = SyncState.SYNCED,
                    lastSyncedAt = System.currentTimeMillis()
                )
                tripDao.insertTrip(newTrip)
            } else if (localTrip.syncState != SyncState.PENDING_DELETE) {
                if (remoteUpdatedAt > localTrip.updatedAt) {
                    val updatedTrip = localTrip.copy(
                        destination = remoteDestination,
                        startDate = remoteStartDate,
                        endDate = remoteEndDate,
                        collaborators = collaboratorsJson,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        syncState = SyncState.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    tripDao.insertTrip(updatedTrip)
                }
            }

            // Sync sub-collections
            syncEventsForTrip(db, tripId, eventDao)
            syncNotesForTrip(db, tripId, noteDao)
            syncRemindersForTrip(db, tripId, reminderDao)
        }

        // Clean up local trips deleted on remote
        val localTrips = tripDao.getAllTrips().first()
        for (localTrip in localTrips) {
            if (localTrip.syncState != SyncState.PENDING_INSERT && localTrip.id !in remoteTripIds) {
                tripDao.deleteTrip(localTrip)
            }
        }

        // 2. Fetch remote packing templates
        val remoteTemplatesSnapshot = db.collection("packing_templates")
            .whereEqualTo("ownerEmail", currentUserEmail)
            .get()
            .await()

        val remoteTemplateIds = mutableSetOf<String>()

        for (templateDoc in remoteTemplatesSnapshot.documents) {
            val templateId = templateDoc.id
            remoteTemplateIds.add(templateId)

            val remoteOwnerEmail = templateDoc.getString("ownerEmail") ?: ""
            val remoteTitle = templateDoc.getString("title") ?: ""
            val remoteItemsList = templateDoc.get("items") as? List<*> ?: emptyList<Any>()
            val remoteItems = remoteItemsList.map { it.toString() }
            val remoteCreatedAt = templateDoc.getLong("createdAt") ?: 0L
            val remoteUpdatedAt = templateDoc.getLong("updatedAt") ?: 0L

            val localTemplate = packingTemplateDao.getTemplateById(templateId).first()
            val itemsJson = Json.encodeToString(remoteItems)

            if (localTemplate == null) {
                val newTemplate = PackingTemplateEntity(
                    id = templateId,
                    ownerEmail = remoteOwnerEmail,
                    title = remoteTitle,
                    items = itemsJson,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    syncState = SyncState.SYNCED,
                    lastSyncedAt = System.currentTimeMillis()
                )
                packingTemplateDao.insertTemplate(newTemplate)
            } else if (localTemplate.syncState != SyncState.PENDING_DELETE) {
                if (remoteUpdatedAt > localTemplate.updatedAt) {
                    val updatedTemplate = localTemplate.copy(
                        ownerEmail = remoteOwnerEmail,
                        title = remoteTitle,
                        items = itemsJson,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        syncState = SyncState.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    packingTemplateDao.insertTemplate(updatedTemplate)
                }
            }
        }

        // Clean up local templates deleted on remote
        val localTemplates = packingTemplateDao.getTemplatesByOwner(currentUserEmail).first()
        for (localTemplate in localTemplates) {
            if (localTemplate.syncState != SyncState.PENDING_INSERT && localTemplate.id !in remoteTemplateIds) {
                packingTemplateDao.deleteTemplate(localTemplate)
            }
        }
    }

    private suspend fun syncEventsForTrip(
        db: FirebaseFirestore,
        tripId: String,
        eventDao: EventDao
    ) {
        val remoteEventsSnapshot = db.collection("trips").document(tripId)
            .collection("events")
            .get()
            .await()

        val remoteEventIds = mutableSetOf<String>()

        for (eventDoc in remoteEventsSnapshot.documents) {
            val eventId = eventDoc.id
            remoteEventIds.add(eventId)

            val remoteTitle = eventDoc.getString("title") ?: ""
            val remoteCategoryName = eventDoc.getString("category") ?: EventCategory.OTHER.name
            val remoteCategory = try {
                EventCategory.valueOf(remoteCategoryName)
            } catch (e: Exception) {
                EventCategory.OTHER
            }
            val remoteLocation = eventDoc.getString("location")
            val remoteTimezone = eventDoc.getString("timezone") ?: "UTC"
            // Falls back to "timezone" when "endTimezone" is absent, so a pre-Phase-4 event
            // document (which only ever had "timezone") still loads correctly with no server
            // migration — it's interpreted as "start and end both in this one timezone."
            val remoteEndTimezone = eventDoc.getString("endTimezone") ?: remoteTimezone
            val remoteStartDate = eventDoc.getString("startDate") ?: ""
            val remoteStartTime = eventDoc.getString("startTime")
            val remoteEndDate = eventDoc.getString("endDate") ?: ""
            val remoteEndTime = eventDoc.getString("endTime")
            val remoteDescription = eventDoc.getString("description")
            val remoteFlightNumber = eventDoc.getString("flightNumber")
            val remoteDepartureAirportCode = eventDoc.getString("departureAirportCode")
            val remoteArrivalAirportCode = eventDoc.getString("arrivalAirportCode")
            val remoteBookingNumber = eventDoc.getString("bookingNumber")
            val remoteCreatedAt = eventDoc.getLong("createdAt") ?: 0L
            val remoteUpdatedAt = eventDoc.getLong("updatedAt") ?: 0L

            val localEvent = eventDao.getEventById(eventId).first()

            if (localEvent == null) {
                val newEvent = EventEntity(
                    id = eventId,
                    tripId = tripId,
                    title = remoteTitle,
                    category = remoteCategory,
                    location = remoteLocation,
                    startTimezone = remoteTimezone,
                    endTimezone = remoteEndTimezone,
                    startDate = remoteStartDate,
                    startTime = remoteStartTime,
                    endDate = remoteEndDate,
                    endTime = remoteEndTime,
                    description = remoteDescription,
                    flightNumber = remoteFlightNumber,
                    departureAirportCode = remoteDepartureAirportCode,
                    arrivalAirportCode = remoteArrivalAirportCode,
                    bookingNumber = remoteBookingNumber,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    syncState = SyncState.SYNCED,
                    lastSyncedAt = System.currentTimeMillis()
                )
                eventDao.insertEvent(newEvent)
            } else if (localEvent.syncState != SyncState.PENDING_DELETE) {
                if (remoteUpdatedAt > localEvent.updatedAt) {
                    val updatedEvent = localEvent.copy(
                        title = remoteTitle,
                        category = remoteCategory,
                        location = remoteLocation,
                        startTimezone = remoteTimezone,
                        endTimezone = remoteEndTimezone,
                        startDate = remoteStartDate,
                        startTime = remoteStartTime,
                        endDate = remoteEndDate,
                        endTime = remoteEndTime,
                        description = remoteDescription,
                        flightNumber = remoteFlightNumber,
                        departureAirportCode = remoteDepartureAirportCode,
                        arrivalAirportCode = remoteArrivalAirportCode,
                        bookingNumber = remoteBookingNumber,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        syncState = SyncState.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    eventDao.insertEvent(updatedEvent)
                }
            }
        }

        val localEvents = eventDao.getEventsByTripId(tripId).first()
        for (localEvent in localEvents) {
            if (localEvent.syncState != SyncState.PENDING_INSERT && localEvent.id !in remoteEventIds) {
                eventDao.deleteEvent(localEvent)
            }
        }
    }

    private suspend fun syncNotesForTrip(
        db: FirebaseFirestore,
        tripId: String,
        noteDao: NoteDao
    ) {
        val remoteNotesSnapshot = db.collection("trips").document(tripId)
            .collection("notes")
            .get()
            .await()

        val remoteNoteIds = mutableSetOf<String>()

        for (noteDoc in remoteNotesSnapshot.documents) {
            val noteId = noteDoc.id
            remoteNoteIds.add(noteId)

            val remoteEventId = noteDoc.getString("eventId")
            val remoteTitle = noteDoc.getString("title") ?: ""
            val remoteTypeName = noteDoc.getString("type") ?: NoteType.TEXT_BLOCK.name
            val remoteType = try {
                NoteType.valueOf(remoteTypeName)
            } catch (e: Exception) {
                NoteType.TEXT_BLOCK
            }
            val remoteContent = noteDoc.getString("content") ?: ""
            val remoteCreatedAt = noteDoc.getLong("createdAt") ?: 0L
            val remoteUpdatedAt = noteDoc.getLong("updatedAt") ?: 0L

            val localNote = noteDao.getNoteById(noteId).first()

            if (localNote == null) {
                val newNote = NoteEntity(
                    id = noteId,
                    tripId = tripId,
                    eventId = remoteEventId,
                    title = remoteTitle,
                    type = remoteType,
                    content = remoteContent,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    syncState = SyncState.SYNCED,
                    lastSyncedAt = System.currentTimeMillis()
                )
                noteDao.insertNote(newNote)
            } else if (localNote.syncState != SyncState.PENDING_DELETE) {
                if (remoteUpdatedAt > localNote.updatedAt) {
                    val updatedNote = localNote.copy(
                        eventId = remoteEventId,
                        title = remoteTitle,
                        type = remoteType,
                        content = remoteContent,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        syncState = SyncState.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    noteDao.insertNote(updatedNote)
                }
            }
        }

        val localNotes = noteDao.getNotesByTripIdSync(tripId)
        for (localNote in localNotes) {
            if (localNote.syncState != SyncState.PENDING_INSERT && localNote.id !in remoteNoteIds) {
                noteDao.deleteNote(localNote)
            }
        }
    }

    private suspend fun syncRemindersForTrip(
        db: FirebaseFirestore,
        tripId: String,
        reminderDao: ReminderDao
    ) {
        val remoteRemindersSnapshot = db.collection("trips").document(tripId)
            .collection("reminders")
            .get()
            .await()

        val remoteReminderIds = mutableSetOf<String>()

        for (reminderDoc in remoteRemindersSnapshot.documents) {
            val reminderId = reminderDoc.id
            remoteReminderIds.add(reminderId)

            val remoteEventId = reminderDoc.getString("eventId")
            val remoteText = reminderDoc.getString("text") ?: ""
            val remoteDate = reminderDoc.getString("date") ?: ""
            val remoteTime = reminderDoc.getString("time") ?: ""
            val remoteCreatedAt = reminderDoc.getLong("createdAt") ?: 0L
            val remoteUpdatedAt = reminderDoc.getLong("updatedAt") ?: 0L

            val localReminder = reminderDao.getReminderById(reminderId).first()

            if (localReminder == null) {
                val newReminder = ReminderEntity(
                    id = reminderId,
                    tripId = tripId,
                    eventId = remoteEventId,
                    text = remoteText,
                    date = remoteDate,
                    time = remoteTime,
                    isTriggeredLocal = false,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    syncState = SyncState.SYNCED,
                    lastSyncedAt = System.currentTimeMillis()
                )
                reminderDao.insertReminder(newReminder)
            } else if (localReminder.syncState != SyncState.PENDING_DELETE) {
                if (remoteUpdatedAt > localReminder.updatedAt) {
                    val updatedReminder = localReminder.copy(
                        eventId = remoteEventId,
                        text = remoteText,
                        date = remoteDate,
                        time = remoteTime,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        syncState = SyncState.SYNCED,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    reminderDao.insertReminder(updatedReminder)
                }
            }
        }

        val localReminders = reminderDao.getRemindersByTripIdSync(tripId)
        for (localReminder in localReminders) {
            if (localReminder.syncState != SyncState.PENDING_INSERT && localReminder.id !in remoteReminderIds) {
                reminderDao.deleteReminder(localReminder)
            }
        }
    }
}

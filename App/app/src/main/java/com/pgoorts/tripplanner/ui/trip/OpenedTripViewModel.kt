package com.pgoorts.tripplanner.ui.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.data.local.entity.roleFor
import com.pgoorts.tripplanner.data.repository.EventRepository
import com.pgoorts.tripplanner.data.repository.NoteRepository
import com.pgoorts.tripplanner.data.repository.PreferencesRepository
import com.pgoorts.tripplanner.data.repository.ReminderRepository
import com.pgoorts.tripplanner.data.repository.TripRepository
import com.pgoorts.tripplanner.photo.CoverPhotoSource
import com.pgoorts.tripplanner.photo.CoverPhotoStorage
import com.pgoorts.tripplanner.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.WorkInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Drives the sync status bar in [com.pgoorts.tripplanner.ui.trip.OpenedTripScreen]. */
enum class SyncBarStatus { IDLE, IN_PROGRESS, FAILED }

/**
 * Represents a single day slot in the itinerary.
 * Multi-day events appear in multiple DayGroup entries.
 */
data class ItineraryDay(
    val date: LocalDate,
    val events: List<ItineraryEvent>
)

data class ItineraryEvent(
    val event: EventEntity,
    /** Null for single-day events; "Day X of Y" for multi-day spanning. */
    val multiDayLabel: String? = null,
    /** True when this day slot is the event's start date (always true for single-day events). */
    val isFirstDay: Boolean = true,
    /** True when this day slot is the event's end date (always true for single-day events). */
    val isLastDay: Boolean = true,
    /** True when the event's own date range isn't fully contained within its trip's (Bug 7). */
    val isInvalid: Boolean = false
)

/**
 * True when [event]'s date range isn't fully contained within [trip]'s current date range.
 * Computed on the fly, never stored (datastructure.txt §5) — narrowing/widening a trip's dates
 * changes this on the next read with no separate "re-validate events" pass needed.
 */
fun isEventInvalid(event: EventEntity, trip: TripEntity): Boolean {
    val tripStart = try { LocalDate.parse(trip.startDate) } catch (e: Exception) { return false }
    val tripEnd = try { LocalDate.parse(trip.endDate) } catch (e: Exception) { return false }
    val evStart = try { LocalDate.parse(event.startDate) } catch (e: Exception) { return false }
    val evEnd = try { LocalDate.parse(event.endDate) } catch (e: Exception) { evStart }
    return evStart.isBefore(tripStart) || evEnd.isAfter(tripEnd)
}

data class OpenedTripUiState(
    val trip: TripEntity? = null,
    val itineraryDays: List<ItineraryDay> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val currentUserRole: TripRole? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class OpenedTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val noteRepository: NoteRepository,
    private val reminderRepository: ReminderRepository,
    private val userSessionManager: UserSessionManager,
    private val preferencesRepository: PreferencesRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    val uiState: StateFlow<OpenedTripUiState> = combine(
        tripRepository.getTripById(tripId),
        eventRepository.getEventsByTripId(tripId),
        noteRepository.getNotesByTripId(tripId),
        reminderRepository.getRemindersByTripId(tripId)
    ) { trip, events, notes, reminders ->
        OpenedTripUiState(
            trip = trip,
            itineraryDays = buildItinerary(trip, events),
            notes = notes,
            reminders = reminders,
            currentUserRole = trip?.roleFor(userSessionManager.userEmail),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OpenedTripUiState()
    )

    /** Saved emails for quick-pick in the Share Trip dialog. */
    val innerCircle: StateFlow<List<String>> = userSessionManager.observeInnerCircle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Global fallback used to pre-fill new events' timezone when this trip has no override. */
    val globalDefaultTimezone: StateFlow<String?> = preferencesRepository.defaultTimezone
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Timestamp of the last fully-successful background/manual sync pass, across all trips. */
    val lastSuccessfulSyncAt: StateFlow<Long?> = preferencesRepository.lastSuccessfulSyncAt
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _syncStatus = MutableStateFlow(SyncBarStatus.IDLE)
    val syncStatus: StateFlow<SyncBarStatus> = _syncStatus.asStateFlow()

    /**
     * Triggers a one-off sync; surfaces a distinct failure state immediately if offline.
     *
     * Observes the freshly-enqueued request by its own ID rather than by the unique work name —
     * the unique-work flow reflects whatever this work name's *last* run (possibly from a prior
     * app session) ended up as, so subscribing to it eagerly on screen open made the bar flash
     * "syncing"/"failed" for an old run that already finished. Scoping to this request's ID means
     * the bar only ever reflects a sync actually happening right now.
     */
    fun triggerManualSync() {
        if (!syncScheduler.isNetworkAvailable()) {
            markSyncFailedThenIdle()
            return
        }
        val workId = syncScheduler.triggerManualSync()
        viewModelScope.launch {
            syncScheduler.observeSyncWorkInfo(workId).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                        _syncStatus.value = SyncBarStatus.IN_PROGRESS
                    WorkInfo.State.SUCCEEDED ->
                        _syncStatus.value = SyncBarStatus.IDLE
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
                        markSyncFailedThenIdle()
                    else -> {}
                }
            }
        }
    }

    private fun markSyncFailedThenIdle() {
        viewModelScope.launch {
            _syncStatus.value = SyncBarStatus.FAILED
            delay(3_000)
            if (_syncStatus.value == SyncBarStatus.FAILED) _syncStatus.value = SyncBarStatus.IDLE
        }
    }

    /**
     * Groups events by day, spanning multi-day events across all days they cover.
     * All-day/no-time events sorted first within a day, then by start time.
     */
    private fun buildItinerary(trip: TripEntity?, events: List<EventEntity>): List<ItineraryDay> {
        if (trip == null) return emptyList()
        val tripStart = try { LocalDate.parse(trip.startDate) } catch (e: Exception) { return emptyList() }
        val tripEnd   = try { LocalDate.parse(trip.endDate)   } catch (e: Exception) { return emptyList() }

        // Day range: the trip's own dates, extended to cover any event whose own dates fall
        // outside it (Bug 1 allows creating those; without this, such an event would have no day
        // slot to render under and be unreachable in the UI — see progress.txt's Block 4 notes).
        val eventDates = events.flatMap { event ->
            listOfNotNull(
                try { LocalDate.parse(event.startDate) } catch (e: Exception) { null },
                try { LocalDate.parse(event.endDate) } catch (e: Exception) { null }
            )
        }
        val rangeStart = (eventDates + tripStart).min()
        val rangeEnd = (eventDates + tripEnd).max()

        val days = mutableListOf<LocalDate>()
        var cursor = rangeStart
        while (!cursor.isAfter(rangeEnd)) {
            days.add(cursor)
            cursor = cursor.plusDays(1)
        }

        return days.map { day ->
            val dayEvents = events.mapNotNull { event ->
                val evStart = try { LocalDate.parse(event.startDate) } catch (e: Exception) { return@mapNotNull null }
                val evEnd   = try { LocalDate.parse(event.endDate)   } catch (e: Exception) { evStart }

                // Event spans this day?
                if (day.isBefore(evStart) || day.isAfter(evEnd)) return@mapNotNull null

                val isMultiDay = !evStart.isEqual(evEnd)
                val multiDayLabel = if (isMultiDay) {
                    val dayIndex = evStart.until(day, java.time.temporal.ChronoUnit.DAYS).toInt() + 1
                    val totalDays = evStart.until(evEnd, java.time.temporal.ChronoUnit.DAYS).toInt() + 1
                    "Day $dayIndex of $totalDays"
                } else null

                ItineraryEvent(
                    event = event,
                    multiDayLabel = multiDayLabel,
                    isFirstDay = day.isEqual(evStart),
                    isLastDay = day.isEqual(evEnd),
                    isInvalid = isEventInvalid(event, trip)
                )
            }.sortedWith(
                compareBy(
                    // All-day (null startTime) first
                    { if (it.event.startTime == null) 0 else 1 },
                    { it.event.startTime ?: "" }
                )
            )
            ItineraryDay(date = day, events = dayEvents)
        }
    }

    // --- Event actions ---
    fun createEvent(
        title: String,
        category: EventCategory,
        startDate: String,
        endDate: String,
        startTime: String? = null,
        endTime: String? = null,
        location: String? = null,
        startTimezone: String = "UTC",
        endTimezone: String = "UTC",
        description: String? = null,
        flightNumber: String? = null,
        departureAirportCode: String? = null,
        arrivalAirportCode: String? = null,
        bookingNumber: String? = null
    ) {
        viewModelScope.launch {
            eventRepository.createEvent(
                tripId = tripId,
                title = title,
                category = category,
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                location = location,
                startTimezone = startTimezone,
                endTimezone = endTimezone,
                description = description,
                flightNumber = flightNumber,
                departureAirportCode = departureAirportCode,
                arrivalAirportCode = arrivalAirportCode,
                bookingNumber = bookingNumber
            )
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { eventRepository.deleteEvent(event) }
    }

    // --- Note actions ---
    fun createNote(
        title: String,
        type: NoteType,
        content: String = "",
        localAttachmentPath: String? = null,
        id: String = java.util.UUID.randomUUID().toString()
    ) {
        viewModelScope.launch {
            noteRepository.createNote(
                tripId = tripId,
                eventId = null,
                title = title,
                type = type,
                content = content,
                localAttachmentPath = localAttachmentPath,
                id = id
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { noteRepository.deleteNote(note) }
    }

    // --- Reminder actions ---
    fun createReminder(text: String, date: String, time: String) {
        viewModelScope.launch {
            reminderRepository.createReminder(
                tripId = tripId,
                eventId = null,
                text = text,
                date = date,
                time = time
            )
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch { reminderRepository.deleteReminder(reminder) }
    }

    // --- Sharing ---
    fun shareTrip(email: String, role: TripRole) {
        viewModelScope.launch {
            tripRepository.addCollaborator(tripId, email, role)
        }
    }

    // --- Trip Settings ---
    /** Sets (or, when null, clears) this trip's timezone override. */
    fun setTripDefaultTimezone(timezone: String?) {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            tripRepository.updateTrip(trip.copy(defaultTimezone = timezone))
        }
    }

    /**
     * Updates this trip's date range (Bug 7). Never touches any event — an event outside the new
     * range simply becomes/stops being flagged invalid the next time it's read (isEventInvalid is
     * fully derived, not stored), so no separate "re-validate events" pass is needed here.
     */
    fun updateTripDates(startDate: String, endDate: String) {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            tripRepository.updateTrip(trip.copy(startDate = startDate, endDate = endDate))
        }
    }

    /** Manual cover-photo override (Bug 6) — always wins over an auto-fetched photo, past or future. */
    fun setCoverPhoto(localPath: String) {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            tripRepository.updateTrip(
                trip.copy(
                    localCoverPhotoPath = localPath,
                    coverPhotoStoragePath = CoverPhotoStorage.storagePath(trip.id),
                    coverPhotoSource = CoverPhotoSource.USER
                )
            )
        }
    }
}

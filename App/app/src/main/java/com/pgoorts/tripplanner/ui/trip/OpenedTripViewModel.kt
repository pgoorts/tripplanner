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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

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
    val multiDayLabel: String? = null
)

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
    private val preferencesRepository: PreferencesRepository
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

    /**
     * Groups events by day, spanning multi-day events across all days they cover.
     * All-day/no-time events sorted first within a day, then by start time.
     */
    private fun buildItinerary(trip: TripEntity?, events: List<EventEntity>): List<ItineraryDay> {
        if (trip == null) return emptyList()
        val tripStart = try { LocalDate.parse(trip.startDate) } catch (e: Exception) { return emptyList() }
        val tripEnd   = try { LocalDate.parse(trip.endDate)   } catch (e: Exception) { return emptyList() }

        // Collect all days in the trip range
        val days = mutableListOf<LocalDate>()
        var cursor = tripStart
        while (!cursor.isAfter(tripEnd)) {
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

                ItineraryEvent(event = event, multiDayLabel = multiDayLabel)
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
        timezone: String = "UTC",
        description: String? = null
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
                timezone = timezone,
                description = description
            )
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { eventRepository.deleteEvent(event) }
    }

    // --- Note actions ---
    fun createNote(title: String, type: NoteType) {
        viewModelScope.launch {
            noteRepository.createNote(
                tripId = tripId,
                eventId = null,
                title = title,
                type = type
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
}

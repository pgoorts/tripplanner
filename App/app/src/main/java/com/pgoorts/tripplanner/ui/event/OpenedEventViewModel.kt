package com.pgoorts.tripplanner.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.data.local.entity.roleFor
import com.pgoorts.tripplanner.data.repository.EventRepository
import com.pgoorts.tripplanner.data.repository.NoteRepository
import com.pgoorts.tripplanner.data.repository.ReminderRepository
import com.pgoorts.tripplanner.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenedEventUiState(
    val event: EventEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val currentUserRole: TripRole? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OpenedEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val noteRepository: NoteRepository,
    private val reminderRepository: ReminderRepository,
    private val tripRepository: TripRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    val eventId: String = checkNotNull(savedStateHandle["eventId"])

    private val eventFlow = eventRepository.getEventById(eventId)

    private val roleFlow = eventFlow.flatMapLatest { event ->
        val tripId = event?.tripId
        if (tripId == null) {
            flowOf(null)
        } else {
            tripRepository.getTripById(tripId).map { it?.roleFor(userSessionManager.userEmail) }
        }
    }

    val uiState: StateFlow<OpenedEventUiState> = combine(
        eventFlow,
        noteRepository.getNotesByEventId(eventId),
        reminderRepository.getRemindersByEventId(eventId),
        roleFlow
    ) { event, notes, reminders, role ->
        OpenedEventUiState(
            event = event,
            notes = notes,
            reminders = reminders,
            currentUserRole = role,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OpenedEventUiState()
    )

    fun updateEvent(
        title: String,
        category: EventCategory,
        location: String?,
        timezone: String,
        startDate: String,
        startTime: String?,
        endDate: String,
        endTime: String?,
        description: String?,
        flightNumber: String?,
        departureAirportCode: String?,
        arrivalAirportCode: String?,
        bookingNumber: String?
    ) {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            eventRepository.updateEvent(
                currentEvent.copy(
                    title = title,
                    category = category,
                    location = location,
                    startTimezone = timezone,
                    endTimezone = timezone,
                    startDate = startDate,
                    startTime = startTime,
                    endDate = endDate,
                    endTime = endTime,
                    description = description,
                    flightNumber = flightNumber,
                    departureAirportCode = departureAirportCode,
                    arrivalAirportCode = arrivalAirportCode,
                    bookingNumber = bookingNumber
                )
            )
        }
    }

    fun addNote(
        title: String,
        type: NoteType,
        content: String = "",
        localAttachmentPath: String? = null,
        id: String = java.util.UUID.randomUUID().toString()
    ) {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            noteRepository.createNote(
                tripId = currentEvent.tripId,
                eventId = eventId,
                title = title,
                type = type,
                content = content,
                localAttachmentPath = localAttachmentPath,
                id = id
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
        }
    }

    fun addReminder(text: String, date: String, time: String) {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            reminderRepository.createReminder(
                tripId = currentEvent.tripId,
                eventId = eventId,
                text = text,
                date = date,
                time = time
            )
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder)
        }
    }
}

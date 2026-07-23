package com.pgoorts.tripplanner.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.repository.EventRepository
import com.pgoorts.tripplanner.data.repository.NoteRepository
import com.pgoorts.tripplanner.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenedEventUiState(
    val event: EventEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class OpenedEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val noteRepository: NoteRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    val eventId: String = checkNotNull(savedStateHandle["eventId"])

    val uiState: StateFlow<OpenedEventUiState> = combine(
        eventRepository.getEventById(eventId),
        noteRepository.getNotesByEventId(eventId),
        reminderRepository.getRemindersByEventId(eventId)
    ) { event, notes, reminders ->
        OpenedEventUiState(
            event = event,
            notes = notes,
            reminders = reminders,
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
        description: String?
    ) {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            eventRepository.updateEvent(
                currentEvent.copy(
                    title = title,
                    category = category,
                    location = location,
                    timezone = timezone,
                    startDate = startDate,
                    startTime = startTime,
                    endDate = endDate,
                    endTime = endTime,
                    description = description
                )
            )
        }
    }

    fun addNote(title: String, type: NoteType) {
        val currentEvent = uiState.value.event ?: return
        viewModelScope.launch {
            noteRepository.createNote(
                tripId = currentEvent.tripId,
                eventId = eventId,
                title = title,
                type = type
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

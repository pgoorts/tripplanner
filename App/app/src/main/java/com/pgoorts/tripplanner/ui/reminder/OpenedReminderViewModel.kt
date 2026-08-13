package com.pgoorts.tripplanner.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import com.pgoorts.tripplanner.data.local.entity.roleFor
import com.pgoorts.tripplanner.data.repository.ReminderRepository
import com.pgoorts.tripplanner.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OpenedReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminderRepository: ReminderRepository,
    private val tripRepository: TripRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val reminderId: String = checkNotNull(savedStateHandle["reminderId"])

    val reminderFlow: StateFlow<ReminderEntity?> = reminderRepository.getReminderById(reminderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val currentUserRole: StateFlow<TripRole?> = reminderFlow.flatMapLatest { reminder ->
        val tripId = reminder?.tripId
        if (tripId == null) {
            flowOf(null)
        } else {
            tripRepository.getTripById(tripId).map { it?.roleFor(userSessionManager.userEmail) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun updateReminder(text: String, date: String, time: String) {
        val currentReminder = reminderFlow.value ?: return
        viewModelScope.launch {
            reminderRepository.updateReminder(
                currentReminder.copy(
                    text = text,
                    date = date,
                    time = time
                )
            )
        }
    }
}

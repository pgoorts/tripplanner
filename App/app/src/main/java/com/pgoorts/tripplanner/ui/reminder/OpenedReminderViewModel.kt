package com.pgoorts.tripplanner.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpenedReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val reminderId: String = checkNotNull(savedStateHandle["reminderId"])

    val reminderFlow: StateFlow<ReminderEntity?> = reminderRepository.getReminderById(reminderId)
        .stateIn(
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

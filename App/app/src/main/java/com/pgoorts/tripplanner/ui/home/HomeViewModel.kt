package com.pgoorts.tripplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val currentTrips: List<TripEntity> = emptyList(),
    val upcomingTrips: List<TripEntity> = emptyList(),
    val pastTrips: List<TripEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    val uiState = tripRepository.getAllTrips()
        .map { trips ->
            val today = LocalDate.now()
            val current = mutableListOf<TripEntity>()
            val upcoming = mutableListOf<TripEntity>()
            val past = mutableListOf<TripEntity>()

            trips.forEach { trip ->
                val start = LocalDate.parse(trip.startDate)
                val end = LocalDate.parse(trip.endDate)
                when {
                    today in start..end -> current.add(trip)
                    start.isAfter(today) -> upcoming.add(trip)
                    else -> past.add(trip)
                }
            }
            HomeUiState(
                currentTrips = current,
                upcomingTrips = upcoming,
                pastTrips = past,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    fun createTrip(destination: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            tripRepository.createTrip(destination, startDate, endDate)
        }
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            tripRepository.deleteTrip(trip)
        }
    }
}

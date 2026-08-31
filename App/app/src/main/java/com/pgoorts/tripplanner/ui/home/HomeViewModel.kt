package com.pgoorts.tripplanner.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.data.repository.TripRepository
import com.pgoorts.tripplanner.photo.CoverPhotoFetcher
import com.pgoorts.tripplanner.photo.CoverPhotoSource
import com.pgoorts.tripplanner.photo.CoverPhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val userSessionManager: UserSessionManager
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

    /**
     * [stagedCoverPhotoPath] is set when the user picked their own photo in Add Trip; otherwise
     * a best-effort auto-fetch (Bug 6) runs after creation and never blocks the trip from
     * appearing — a failed/slow fetch just leaves the offline illustration fallback in place.
     */
    fun createTrip(destination: String, startDate: String, endDate: String, stagedCoverPhotoPath: String? = null) {
        viewModelScope.launch {
            val trip = tripRepository.createTrip(
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                ownerEmail = userSessionManager.userEmail ?: "",
                localCoverPhotoPath = stagedCoverPhotoPath,
                coverPhotoSource = if (stagedCoverPhotoPath != null) CoverPhotoSource.USER else null
            )
            if (stagedCoverPhotoPath == null) {
                launch {
                    val result = CoverPhotoFetcher.fetchCoverPhoto(context, trip.destination) ?: return@launch
                    // Re-read before writing — a manual override from Trip Settings could have
                    // raced this fetch and already set a source, which must never be clobbered.
                    val current = tripRepository.getTripById(trip.id).first()
                    if (current != null && current.coverPhotoSource == null) {
                        val localPath = CoverPhotoStorage.stageBytes(context, result.bytes)
                        tripRepository.updateTrip(
                            current.copy(
                                localCoverPhotoPath = localPath,
                                coverPhotoStoragePath = CoverPhotoStorage.storagePath(current.id),
                                coverPhotoSource = result.source
                            )
                        )
                    }
                }
            }
        }
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            tripRepository.deleteTrip(trip)
        }
    }
}

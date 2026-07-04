package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.TripDao
import com.pgoorts.tripplanner.data.local.entity.SyncState
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao
) {
    fun getAllTrips(): Flow<List<TripEntity>> = tripDao.getAllTrips()

    fun getTripById(tripId: String): Flow<TripEntity?> = tripDao.getTripById(tripId)

    suspend fun insertTrip(trip: TripEntity) {
        tripDao.insertTrip(trip)
    }

    suspend fun createTrip(
        destination: String,
        startDate: String,
        endDate: String,
        ownerEmail: String = ""
    ): TripEntity {
        val collaborators = if (ownerEmail.isNotBlank()) {
            """{"$ownerEmail":"CO_OWNER"}"""
        } else {
            "{}"
        }
        val now = System.currentTimeMillis()
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            collaborators = collaborators,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_INSERT
        )
        tripDao.insertTrip(trip)
        return trip
    }

    suspend fun updateTrip(trip: TripEntity) {
        tripDao.updateTrip(trip.copy(
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING_UPDATE
        ))
    }

    suspend fun deleteTrip(trip: TripEntity) {
        // Soft delete: mark as PENDING_DELETE for sync engine, then remove locally
        tripDao.deleteTripById(trip.id)
    }
}

package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.TripDao
import com.pgoorts.tripplanner.data.local.entity.SyncState
import com.pgoorts.tripplanner.data.local.entity.TripEntity
import com.pgoorts.tripplanner.data.local.entity.TripRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    /** Adds or updates a collaborator's role on the trip and marks it pending sync. */
    suspend fun addCollaborator(tripId: String, email: String, role: TripRole) {
        val trip = tripDao.getTripById(tripId).first() ?: return
        val current = try {
            Json.decodeFromString<Map<String, String>>(trip.collaborators).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
        current[email] = role.name
        updateTrip(trip.copy(collaborators = Json.encodeToString(current)))
    }

    suspend fun deleteTrip(trip: TripEntity) {
        if (trip.syncState == SyncState.PENDING_INSERT) {
            tripDao.deleteTripById(trip.id)
        } else {
            tripDao.insertTrip(trip.copy(
                syncState = SyncState.PENDING_DELETE,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}

package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.EventDao
import com.pgoorts.tripplanner.data.local.entity.EventCategory
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import com.pgoorts.tripplanner.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao
) {
    fun getEventsByTripId(tripId: String): Flow<List<EventEntity>> =
        eventDao.getEventsByTripId(tripId)

    fun getEventById(eventId: String): Flow<EventEntity?> =
        eventDao.getEventById(eventId)

    suspend fun createEvent(
        tripId: String,
        title: String,
        category: EventCategory,
        startDate: String,
        endDate: String,
        startTime: String? = null,
        endTime: String? = null,
        location: String? = null,
        timezone: String = "UTC",
        description: String? = null
    ): EventEntity {
        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = title,
            category = category,
            location = location,
            timezone = timezone,
            startDate = startDate,
            startTime = startTime,
            endDate = endDate,
            endTime = endTime,
            description = description,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_INSERT
        )
        eventDao.insertEvent(event)
        return event
    }

    suspend fun updateEvent(event: EventEntity) {
        eventDao.updateEvent(
            event.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING_UPDATE
            )
        )
    }

    suspend fun deleteEvent(event: EventEntity) {
        eventDao.deleteEventById(event.id)
    }
}

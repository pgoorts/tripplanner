package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao
) {
    fun getRemindersByTripId(tripId: String): Flow<List<ReminderEntity>> =
        reminderDao.getRemindersByTripId(tripId)

    fun getRemindersByEventId(eventId: String): Flow<List<ReminderEntity>> =
        reminderDao.getRemindersByEventId(eventId)

    fun getReminderById(reminderId: String): Flow<ReminderEntity?> =
        reminderDao.getReminderById(reminderId)

    suspend fun createReminder(
        tripId: String,
        eventId: String? = null,
        text: String,
        date: String,
        time: String
    ): ReminderEntity {
        val now = System.currentTimeMillis()
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            eventId = eventId,
            text = text,
            date = date,
            time = time,
            isTriggeredLocal = false,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_INSERT
        )
        reminderDao.insertReminder(reminder)
        return reminder
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(
            reminder.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING_UPDATE
            )
        )
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminderById(reminder.id)
    }
}

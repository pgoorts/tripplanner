package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import com.pgoorts.tripplanner.data.local.entity.SyncState
import com.pgoorts.tripplanner.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler
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
        reminderScheduler.schedule(reminder)
        return reminder
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        val updatedReminder = reminder.copy(
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING_UPDATE
        )
        reminderDao.updateReminder(updatedReminder)
        reminderScheduler.schedule(updatedReminder)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderScheduler.cancel(reminder)
        if (reminder.syncState == SyncState.PENDING_INSERT) {
            reminderDao.deleteReminderById(reminder.id)
        } else {
            reminderDao.insertReminder(reminder.copy(
                syncState = SyncState.PENDING_DELETE,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}

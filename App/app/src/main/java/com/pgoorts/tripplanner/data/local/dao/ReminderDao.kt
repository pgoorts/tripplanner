package com.pgoorts.tripplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pgoorts.tripplanner.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("""
        SELECT * FROM reminders 
        WHERE tripId = :tripId AND eventId IS NULL AND syncState != 'PENDING_DELETE'
        ORDER BY date ASC, time ASC
    """)
    fun getRemindersByTripId(tripId: String): Flow<List<ReminderEntity>>

    @Query("""
        SELECT * FROM reminders 
        WHERE eventId = :eventId AND syncState != 'PENDING_DELETE'
        ORDER BY date ASC, time ASC
    """)
    fun getRemindersByEventId(eventId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    fun getReminderById(reminderId: String): Flow<ReminderEntity?>

    @Query("""
        SELECT * FROM reminders 
        WHERE syncState != 'PENDING_DELETE' AND isTriggeredLocal = 0
        ORDER BY date ASC, time ASC
    """)
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Query("SELECT * FROM reminders WHERE syncState != 'SYNCED'")
    suspend fun getPendingSyncReminders(): List<ReminderEntity>
}

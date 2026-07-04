package com.pgoorts.tripplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pgoorts.tripplanner.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("""
        SELECT * FROM events 
        WHERE tripId = :tripId AND syncState != 'PENDING_DELETE'
        ORDER BY startDate ASC, CASE WHEN startTime IS NULL THEN 0 ELSE 1 END ASC, startTime ASC
    """)
    fun getEventsByTripId(tripId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :eventId")
    fun getEventById(eventId: String): Flow<EventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: String)

    @Query("SELECT * FROM events WHERE syncState != 'SYNCED'")
    suspend fun getPendingSyncEvents(): List<EventEntity>
}

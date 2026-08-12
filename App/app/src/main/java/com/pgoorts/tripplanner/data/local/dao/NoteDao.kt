package com.pgoorts.tripplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("""
        SELECT * FROM notes 
        WHERE tripId = :tripId AND eventId IS NULL AND syncState != 'PENDING_DELETE'
        ORDER BY updatedAt DESC
    """)
    fun getNotesByTripId(tripId: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE eventId = :eventId AND syncState != 'PENDING_DELETE'
        ORDER BY updatedAt DESC
    """)
    fun getNotesByEventId(eventId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun getNoteById(noteId: String): Flow<NoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    @Query("SELECT * FROM notes WHERE syncState != 'SYNCED'")
    suspend fun getPendingSyncNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE tripId = :tripId")
    suspend fun getNotesByTripIdSync(tripId: String): List<NoteEntity>
}

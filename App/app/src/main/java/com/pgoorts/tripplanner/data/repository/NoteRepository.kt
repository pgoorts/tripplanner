package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.NoteDao
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.NoteType
import com.pgoorts.tripplanner.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getNotesByTripId(tripId: String): Flow<List<NoteEntity>> =
        noteDao.getNotesByTripId(tripId)

    fun getNotesByEventId(eventId: String): Flow<List<NoteEntity>> =
        noteDao.getNotesByEventId(eventId)

    fun getNoteById(noteId: String): Flow<NoteEntity?> =
        noteDao.getNoteById(noteId)

    suspend fun createNote(
        tripId: String,
        eventId: String? = null,
        title: String,
        type: NoteType,
        content: String = "",
        localAttachmentPath: String? = null,
        id: String = UUID.randomUUID().toString()
    ): NoteEntity {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = id,
            tripId = tripId,
            eventId = eventId,
            title = title,
            type = type,
            content = content,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_INSERT,
            localAttachmentPath = localAttachmentPath
        )
        noteDao.insertNote(note)
        return note
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(
            note.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING_UPDATE
            )
        )
    }

    suspend fun deleteNote(note: NoteEntity) {
        if (note.syncState == SyncState.PENDING_INSERT) {
            noteDao.deleteNoteById(note.id)
        } else {
            noteDao.insertNote(note.copy(
                syncState = SyncState.PENDING_DELETE,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}

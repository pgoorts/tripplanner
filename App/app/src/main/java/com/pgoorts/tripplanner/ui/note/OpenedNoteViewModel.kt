package com.pgoorts.tripplanner.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.data.local.entity.ChecklistItem
import com.pgoorts.tripplanner.data.local.entity.NoteEntity
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import com.pgoorts.tripplanner.data.repository.NoteRepository
import com.pgoorts.tripplanner.data.repository.PackingTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class OpenedNoteUiState(
    val note: NoteEntity? = null,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val templates: List<PackingTemplateEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class OpenedNoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val packingTemplateRepository: PackingTemplateRepository
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

    val uiState: StateFlow<OpenedNoteUiState> = combine(
        noteRepository.getNoteById(noteId),
        packingTemplateRepository.getTemplatesByOwner("") // Use blank email for local-only templates
    ) { note, templates ->
        val checklistItems = if (note?.type == com.pgoorts.tripplanner.data.local.entity.NoteType.CHECKLIST) {
            try {
                Json.decodeFromString<List<ChecklistItem>>(note.content)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        OpenedNoteUiState(
            note = note,
            checklistItems = checklistItems,
            templates = templates,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OpenedNoteUiState()
    )

    fun updateNoteTitle(title: String) {
        val currentNote = uiState.value.note ?: return
        viewModelScope.launch {
            noteRepository.updateNote(
                currentNote.copy(title = title)
            )
        }
    }

    fun updateTextContent(content: String) {
        val currentNote = uiState.value.note ?: return
        viewModelScope.launch {
            noteRepository.updateNote(
                currentNote.copy(content = content)
            )
        }
    }

    // Checklist operations
    fun toggleChecklistItem(index: Int) {
        val currentNote = uiState.value.note ?: return
        val currentItems = uiState.value.checklistItems.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            currentItems[index] = item.copy(isChecked = !item.isChecked)
            saveChecklistItems(currentNote, currentItems)
        }
    }

    fun addChecklistItem(text: String) {
        val currentNote = uiState.value.note ?: return
        if (text.isBlank()) return
        val currentItems = uiState.value.checklistItems.toMutableList()
        currentItems.add(ChecklistItem(text = text.trim(), isChecked = false))
        saveChecklistItems(currentNote, currentItems)
    }

    fun removeChecklistItem(index: Int) {
        val currentNote = uiState.value.note ?: return
        val currentItems = uiState.value.checklistItems.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            saveChecklistItems(currentNote, currentItems)
        }
    }

    fun mergeTemplate(template: PackingTemplateEntity) {
        val currentNote = uiState.value.note ?: return
        val templateItems = try {
            Json.decodeFromString<List<String>>(template.items)
        } catch (e: Exception) {
            emptyList()
        }

        val currentItems = uiState.value.checklistItems.toMutableList()
        templateItems.forEach { itemText ->
            // Prevent exact duplicates
            if (currentItems.none { it.text.equals(itemText, ignoreCase = true) }) {
                currentItems.add(ChecklistItem(text = itemText, isChecked = false))
            }
        }
        saveChecklistItems(currentNote, currentItems)
    }

    private fun saveChecklistItems(note: NoteEntity, items: List<ChecklistItem>) {
        viewModelScope.launch {
            val contentJson = Json.encodeToString(items)
            noteRepository.updateNote(
                note.copy(content = contentJson)
            )
        }
    }
}

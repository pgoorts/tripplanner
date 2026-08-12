package com.pgoorts.tripplanner.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import com.pgoorts.tripplanner.data.repository.PackingTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    val userSessionManager: UserSessionManager,
    private val packingTemplateRepository: PackingTemplateRepository
) : ViewModel() {

    private val jsonCodec = Json { ignoreUnknownKeys = true }

    // --- Inner Circle ---
    val innerCircle: StateFlow<List<String>> = userSessionManager.observeInnerCircle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveInnerCircle(emails: List<String>) {
        viewModelScope.launch { userSessionManager.saveInnerCircle(emails) }
    }

    // --- Packing Templates ---
    val templates: StateFlow<List<PackingTemplateEntity>> = userSessionManager
        .observeInnerCircle()
        .flatMapLatest {
            val email = userSessionManager.userEmail ?: return@flatMapLatest flowOf(emptyList())
            packingTemplateRepository.getTemplatesByOwner(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createTemplate(title: String, items: List<String>) {
        val email = userSessionManager.userEmail ?: return
        viewModelScope.launch {
            packingTemplateRepository.createTemplate(
                ownerEmail = email,
                title = title,
                itemsJson = encodeItems(items)
            )
        }
    }

    fun updateTemplate(template: PackingTemplateEntity, newTitle: String, newItems: List<String>) {
        viewModelScope.launch {
            packingTemplateRepository.updateTemplate(
                template.copy(
                    title = newTitle,
                    items = encodeItems(newItems)
                )
            )
        }
    }

    fun deleteTemplate(template: PackingTemplateEntity) {
        viewModelScope.launch { packingTemplateRepository.deleteTemplate(template) }
    }

    fun decodeItems(json: String): List<String> = try {
        jsonCodec.decodeFromString(ListSerializer(String.serializer()), json)
    } catch (e: Exception) { emptyList() }

    private fun encodeItems(items: List<String>): String =
        jsonCodec.encodeToString(ListSerializer(String.serializer()), items)
}

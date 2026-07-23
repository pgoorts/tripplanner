package com.pgoorts.tripplanner.data.repository

import com.pgoorts.tripplanner.data.local.dao.PackingTemplateDao
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import com.pgoorts.tripplanner.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackingTemplateRepository @Inject constructor(
    private val packingTemplateDao: PackingTemplateDao
) {
    fun getTemplatesByOwner(ownerEmail: String): Flow<List<PackingTemplateEntity>> =
        packingTemplateDao.getTemplatesByOwner(ownerEmail)

    fun getTemplateById(templateId: String): Flow<PackingTemplateEntity?> =
        packingTemplateDao.getTemplateById(templateId)

    suspend fun createTemplate(
        ownerEmail: String,
        title: String,
        itemsJson: String
    ): PackingTemplateEntity {
        val now = System.currentTimeMillis()
        val template = PackingTemplateEntity(
            id = UUID.randomUUID().toString(),
            ownerEmail = ownerEmail,
            title = title,
            items = itemsJson,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_INSERT
        )
        packingTemplateDao.insertTemplate(template)
        return template
    }

    suspend fun updateTemplate(template: PackingTemplateEntity) {
        packingTemplateDao.updateTemplate(
            template.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING_UPDATE
            )
        )
    }

    suspend fun deleteTemplate(template: PackingTemplateEntity) {
        packingTemplateDao.deleteTemplateById(template.id)
    }
}

package com.pgoorts.tripplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pgoorts.tripplanner.data.local.entity.PackingTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackingTemplateDao {

    @Query("""
        SELECT * FROM packing_templates 
        WHERE ownerEmail = :ownerEmail AND syncState != 'PENDING_DELETE'
        ORDER BY title ASC
    """)
    fun getTemplatesByOwner(ownerEmail: String): Flow<List<PackingTemplateEntity>>

    @Query("SELECT * FROM packing_templates WHERE id = :templateId")
    fun getTemplateById(templateId: String): Flow<PackingTemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: PackingTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<PackingTemplateEntity>)

    @Update
    suspend fun updateTemplate(template: PackingTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: PackingTemplateEntity)

    @Query("DELETE FROM packing_templates WHERE id = :templateId")
    suspend fun deleteTemplateById(templateId: String)

    @Query("SELECT * FROM packing_templates WHERE syncState != 'SYNCED'")
    suspend fun getPendingSyncTemplates(): List<PackingTemplateEntity>
}

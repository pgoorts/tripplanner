package com.pgoorts.tripplanner.di

import android.content.Context
import androidx.room.Room
import com.pgoorts.tripplanner.data.local.AppDatabase
import com.pgoorts.tripplanner.data.local.MIGRATION_1_2
import com.pgoorts.tripplanner.data.local.dao.EventDao
import com.pgoorts.tripplanner.data.local.dao.NoteDao
import com.pgoorts.tripplanner.data.local.dao.PackingTemplateDao
import com.pgoorts.tripplanner.data.local.dao.ReminderDao
import com.pgoorts.tripplanner.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tripplanner.db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    @Singleton
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    @Singleton
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun providePackingTemplateDao(db: AppDatabase): PackingTemplateDao = db.packingTemplateDao()
}

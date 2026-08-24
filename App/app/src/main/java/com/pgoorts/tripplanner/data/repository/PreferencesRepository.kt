package com.pgoorts.tripplanner.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pgoorts.tripplanner.auth.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-device, local-only preferences (not synced to Firestore) — datastructure.txt §6. */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 15

        private val KEY_DEFAULT_TIMEZONE = stringPreferencesKey("defaultTimezone")
        private val KEY_SYNC_INTERVAL_MINUTES = intPreferencesKey("syncIntervalMinutes")
        private val KEY_LAST_SUCCESSFUL_SYNC_AT = longPreferencesKey("lastSuccessfulSyncAt")
    }

    val defaultTimezone: Flow<String?> = context.dataStore.data.map { it[KEY_DEFAULT_TIMEZONE] }

    suspend fun setDefaultTimezone(timezone: String?) {
        context.dataStore.edit { prefs ->
            if (timezone.isNullOrBlank()) {
                prefs.remove(KEY_DEFAULT_TIMEZONE)
            } else {
                prefs[KEY_DEFAULT_TIMEZONE] = timezone
            }
        }
    }

    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_SYNC_INTERVAL_MINUTES] ?: DEFAULT_SYNC_INTERVAL_MINUTES
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_SYNC_INTERVAL_MINUTES] = minutes }
    }

    val lastSuccessfulSyncAt: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_SUCCESSFUL_SYNC_AT] }

    suspend fun setLastSuccessfulSyncAt(epochMillis: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_SUCCESSFUL_SYNC_AT] = epochMillis }
    }
}

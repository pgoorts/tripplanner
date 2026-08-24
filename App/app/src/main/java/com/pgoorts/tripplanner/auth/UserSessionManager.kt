package com.pgoorts.tripplanner.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private val KEY_INNER_CIRCLE = stringPreferencesKey("inner_circle")
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    val userEmail: String?
        get() = auth.currentUser?.email

    val userDisplayName: String?
        get() = auth.currentUser?.displayName

    val userPhotoUrl: String?
        get() = auth.currentUser?.photoUrl?.toString()

    fun signOut() {
        auth.signOut()
    }

    /** Persists inner-circle email list to DataStore */
    suspend fun saveInnerCircle(emails: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INNER_CIRCLE] = Json.encodeToString(emails)
        }
    }

    /** Observes inner-circle email list from DataStore */
    fun observeInnerCircle(): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_INNER_CIRCLE] ?: return@map emptyList()
            try {
                Json.decodeFromString<List<String>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
}

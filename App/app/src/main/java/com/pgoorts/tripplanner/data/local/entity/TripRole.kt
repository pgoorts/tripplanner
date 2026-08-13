package com.pgoorts.tripplanner.data.local.entity

import kotlinx.serialization.json.Json

enum class TripRole {
    CO_OWNER,
    VIEWER
}

/**
 * Looks up [email]'s role in this trip's `collaborators` JSON map.
 * Returns null if the email has no entry (treated as unrestricted by callers,
 * since the Firestore rules remain the real write-access backstop).
 */
fun TripEntity.roleFor(email: String?): TripRole? {
    if (email.isNullOrBlank()) return null
    val map = try {
        Json.decodeFromString<Map<String, String>>(collaborators)
    } catch (e: Exception) {
        return null
    }
    val roleName = map[email] ?: return null
    return try {
        TripRole.valueOf(roleName)
    } catch (e: IllegalArgumentException) {
        null
    }
}

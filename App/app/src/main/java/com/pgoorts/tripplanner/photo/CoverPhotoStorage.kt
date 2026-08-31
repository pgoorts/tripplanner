package com.pgoorts.tripplanner.photo

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Local-only staging for a trip's cover photo (auto-fetched or user-picked) plus the
 * deterministic Storage path every trip's cover photo ends up at once synced — per
 * datastructure.txt §2 ("why auto-fetched photos are re-uploaded to our own Storage").
 */
object CoverPhotoStorage {

    fun storagePath(tripId: String): String = "trips/$tripId/cover.jpg"

    /** Stages already-fetched image bytes into app-internal storage; returns the local path. */
    fun stageBytes(context: Context, bytes: ByteArray): String {
        val file = File(File(context.filesDir, "covers"), "${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** Copies a user-picked photo Uri into app-internal storage; returns the local path. */
    fun stagePicked(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open picked photo")
        return stageBytes(context, bytes)
    }
}

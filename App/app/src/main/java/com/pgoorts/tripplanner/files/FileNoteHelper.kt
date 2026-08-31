package com.pgoorts.tripplanner.files

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A `FILE` note's flattened `content` shape, per datastructure.txt §3. */
@Serializable
data class FileNoteContent(
    val storagePath: String,
    val originalFileName: String,
    val mimeType: String
)

/**
 * Generic-file-attachment note type (item 9), a direct rerun of Phase 3's Pkpass note pattern:
 * pick a file, stage it locally, upload it via `SyncWorker`'s (now-generalized) Pkpass attachment
 * logic, and open a downloaded copy through the app's `FileProvider`.
 */
object FileNoteHelper {

    fun buildStoragePath(tripId: String, noteId: String, originalFileName: String): String =
        "files/$tripId/$noteId/$originalFileName"

    private fun localFilePath(context: Context, noteId: String, originalFileName: String): String =
        File(File(context.filesDir, "files/$noteId"), originalFileName).absolutePath

    /** Copies the picked file's raw bytes into app-internal storage; returns the local path. */
    fun copyToLocalStorage(context: Context, uri: Uri, noteId: String, originalFileName: String): String {
        val outFile = File(localFilePath(context, noteId, originalFileName))
        outFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open picked file")
        return outFile.absolutePath
    }

    fun parseContent(content: String): FileNoteContent? = try {
        Json.decodeFromString<FileNoteContent>(content)
    } catch (e: Exception) {
        null
    }

    /** Downloads the Storage object to app-internal storage if it isn't already cached there. */
    suspend fun ensureDownloaded(context: Context, noteId: String, fileContent: FileNoteContent): File {
        val localFile = File(localFilePath(context, noteId, fileContent.originalFileName))
        if (localFile.exists()) return localFile
        localFile.parentFile?.mkdirs()
        FirebaseStorage.getInstance().reference.child(fileContent.storagePath).getFile(localFile).await()
        return localFile
    }
}

package com.pgoorts.tripplanner.pkpass

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Unzips an imported `.pkpass` file, reads `pass.json`, and flattens it into [PkpassContent] per
 * DesignDocs/Phase3/datastructure.txt §3. Only `pass.json` is read — no image assets/signature.
 */
object PkpassParser {

    private val FIELD_GROUPS = listOf("primaryFields", "secondaryFields", "auxiliaryFields", "backFields")
    private val PASS_STYLES = listOf("boardingPass", "coupon", "eventTicket", "generic", "storeCard")

    fun buildStoragePath(tripId: String, noteId: String): String =
        "pkpass/$tripId/$noteId/original.pkpass"

    fun localFilePath(context: Context, noteId: String): String =
        File(File(context.filesDir, "pkpass/$noteId"), "original.pkpass").absolutePath

    fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "pass.pkpass"
    }

    /** Copies the picked file's raw bytes into app-internal storage; returns the local path. */
    fun copyToLocalStorage(context: Context, uri: Uri, noteId: String): String {
        val outFile = File(localFilePath(context, noteId))
        outFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open picked file")
        return outFile.absolutePath
    }

    /** Reads `pass.json` out of the `.pkpass` zip and builds the renderable content shape. */
    fun parse(context: Context, uri: Uri, storagePath: String, originalFileName: String): PkpassContent? {
        val passJsonBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "pass.json") return@use zip.readBytes()
                    entry = zip.nextEntry
                }
                null
            }
        } ?: return null

        val json = Json.parseToJsonElement(String(passJsonBytes, Charsets.UTF_8)).jsonObject
        val passType = PASS_STYLES.firstOrNull { json.containsKey(it) } ?: "generic"
        val styleObj = json[passType]?.jsonObject

        val fields = mutableListOf<PkpassField>()
        FIELD_GROUPS.forEach { group ->
            (styleObj?.get(group) as? JsonArray)?.forEach { element ->
                val obj = element.jsonObject
                val key = obj["key"]?.jsonPrimitive?.content ?: return@forEach
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: key
                val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                fields.add(PkpassField(key = key, label = label, value = value))
            }
        }

        val barcodeObj: JsonObject? =
            (json["barcodes"] as? JsonArray)?.firstOrNull()?.jsonObject ?: json["barcode"]?.jsonObject
        val barcodeMessage = barcodeObj?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
        val rawFormat = barcodeObj?.get("format")?.jsonPrimitive?.contentOrNull ?: ""
        val barcodeFormat = when {
            rawFormat.contains("PDF417", ignoreCase = true) -> "PDF417"
            rawFormat.contains("Aztec", ignoreCase = true) -> "AZTEC"
            rawFormat.contains("Code128", ignoreCase = true) -> "CODE128"
            else -> "QR"
        }

        return PkpassContent(
            storagePath = storagePath,
            originalFileName = originalFileName,
            passType = passType,
            organizationName = json["organizationName"]?.jsonPrimitive?.contentOrNull ?: "",
            description = json["description"]?.jsonPrimitive?.contentOrNull ?: "",
            serialNumber = json["serialNumber"]?.jsonPrimitive?.contentOrNull ?: "",
            barcodeMessage = barcodeMessage,
            barcodeFormat = barcodeFormat,
            fields = fields
        )
    }

    /** Pulls just `content.storagePath` back out of a persisted Note's JSON `content` string. */
    fun extractStoragePath(content: String): String? = try {
        Json.parseToJsonElement(content).jsonObject["storagePath"]?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null
    }
}

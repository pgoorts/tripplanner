package com.pgoorts.tripplanner.data.local.entity

import kotlinx.serialization.Serializable

enum class NoteType {
    TEXT_BLOCK,
    CHECKLIST,
    WEB_URL,
    GOOGLE_DOC,
    GOOGLE_DRIVE,
    PKPASS,
    FILE
}

/** Classifies a pasted link into the specific [NoteType] it represents, per description_detail.txt §7. */
fun classifyNoteUrl(url: String): NoteType {
    val normalized = url.trim().lowercase()
    return when {
        normalized.contains("docs.google.com") ||
            normalized.contains("sheets.google.com") ||
            normalized.contains("slides.google.com") -> NoteType.GOOGLE_DOC
        normalized.contains("drive.google.com") -> NoteType.GOOGLE_DRIVE
        else -> NoteType.WEB_URL
    }
}

/**
 * A `GOOGLE_DRIVE` note's second `content` shape (item 8): a file picked via the system Drive
 * picker rather than a pasted share link, per datastructure.txt §3. Rendering distinguishes this
 * from the plain-URL shape by attempting to parse `content` as this type first.
 */
@Serializable
data class DrivePickerContent(val uri: String, val displayName: String)

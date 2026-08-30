package com.pgoorts.tripplanner.data.local.entity

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

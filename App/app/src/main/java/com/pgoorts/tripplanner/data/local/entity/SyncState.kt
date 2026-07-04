package com.pgoorts.tripplanner.data.local.entity

enum class SyncState {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE
}

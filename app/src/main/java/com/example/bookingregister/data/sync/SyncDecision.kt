package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState

interface SyncComparableEntity {
    val syncState: String
    val revision: Long
    val updatedAt: Long
}

fun shouldAcceptRemoteFoodEntity(
    localSyncState: String,
    localRevision: Long,
    localUpdatedAt: Long,
    remoteRevision: Long,
    remoteUpdatedAt: Long
): Boolean {
    if (localSyncState == SyncState.PENDING || localSyncState == SyncState.FAILED) {
        return false
    }

    return remoteRevision > localRevision || remoteUpdatedAt >= localUpdatedAt
}

fun shouldAcceptRemotePaymentEntity(
    localSyncState: String,
    localRevision: Long,
    localUpdatedAt: Long,
    remoteRevision: Long,
    remoteUpdatedAt: Long
): Boolean {
    if (localSyncState == SyncState.PENDING) return false
    if (localSyncState == SyncState.FAILED && remoteRevision >= localRevision) return true
    return remoteRevision > localRevision || remoteUpdatedAt >= localUpdatedAt
}

fun syncBoundary(localCount: Int, maxUpdatedAt: Long?): Long? {
    return if (localCount <= 0) null else maxUpdatedAt?.coerceAtLeast(0L)
}

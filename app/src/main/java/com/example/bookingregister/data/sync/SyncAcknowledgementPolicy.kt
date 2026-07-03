package com.example.bookingregister.data.sync

object SyncAcknowledgementPolicy {
    fun isSameVersion(
        sentUpdatedAt: Long,
        sentRevision: Long,
        sentBaseRevision: Long,
        currentUpdatedAt: Long,
        currentRevision: Long,
        currentBaseRevision: Long
    ): Boolean = sentUpdatedAt == currentUpdatedAt &&
        sentRevision == currentRevision &&
        sentBaseRevision == currentBaseRevision
}

package com.example.bookingregister.data.sync

import com.example.bookingregister.booking.domain.BookingChangeSet
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity

/**
 * Resolves only a proven false orphan: there is no durable local command and every
 * receptionist-editable booking field already matches the cloud booking. A genuine
 * local/cloud difference remains failed for explicit human review.
 */
object BookingOrphanReconciliationPolicy {
    fun canAcceptMatchingCloudBooking(
        local: BookingEntity,
        cloud: BookingEntity,
        hasPendingIntent: Boolean
    ): Boolean {
        if (hasPendingIntent) return false
        if (local.syncState != SyncState.FAILED) return false
        if (!isOrphanedBookingIntentFailure(local.lastSyncError)) return false

        return !BookingChangeSet.create(
            previous = cloud,
            requested = local,
            previousLines = emptyList(),
            requestedLines = emptyList()
        ).hasChanges
    }
}

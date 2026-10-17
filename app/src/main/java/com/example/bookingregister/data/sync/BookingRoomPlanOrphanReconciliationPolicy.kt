package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity

/**
 * Clears a stranded room-plan status only when the cloud already contains the exact
 * same assignment and there is no durable local booking command left to upload.
 */
object BookingRoomPlanOrphanReconciliationPolicy {
    fun canAcceptMatchingCloudAssignment(
        local: BookingRoomNightAssignmentEntity,
        cloud: BookingRoomNightAssignmentEntity,
        hasPendingIntent: Boolean
    ): Boolean {
        if (hasPendingIntent) return false

        val isRecoverableOrphan =
            local.syncState == SyncState.PENDING ||
                (
                    local.syncState == SyncState.FAILED &&
                        isOrphanedBookingIntentFailure(local.lastSyncError)
                )
        if (!isRecoverableOrphan) return false
        if (cloud.revision < local.revision) return false

        return local.remoteId == cloud.remoteId &&
            local.hotelRemoteId == cloud.hotelRemoteId &&
            local.bookingRemoteId == cloud.bookingRemoteId &&
            local.roomRemoteId == cloud.roomRemoteId &&
            local.propertyRemoteId.orEmpty() == cloud.propertyRemoteId.orEmpty() &&
            local.businessDateMillis == cloud.businessDateMillis &&
            local.isDeleted == cloud.isDeleted
    }
}

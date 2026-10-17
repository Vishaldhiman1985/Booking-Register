package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingFinancialLineEntity

/**
 * Reconciles a stranded financial-line status only when every immutable accounting
 * value already matches an equal-or-newer authoritative cloud row.
 */
object BookingFinancialLineOrphanReconciliationPolicy {
    fun canAcceptMatchingCloudLine(
        local: BookingFinancialLineEntity,
        cloud: BookingFinancialLineEntity,
        hasPendingFinancialIntent: Boolean
    ): Boolean {
        if (hasPendingFinancialIntent) return false

        val isRecoverableOrphan =
            local.syncState == SyncState.PENDING ||
                (
                    local.syncState == SyncState.FAILED &&
                        isOrphanedBookingIntentFailure(local.lastSyncError)
                )
        if (!isRecoverableOrphan) return false
        if (cloud.revision < local.revision) return false

        return hasSameAccountingContent(local, cloud)
    }

    fun hasSameAccountingContent(
        first: BookingFinancialLineEntity,
        second: BookingFinancialLineEntity
    ): Boolean = first.remoteId == second.remoteId &&
        first.hotelRemoteId == second.hotelRemoteId &&
        first.bookingRemoteId == second.bookingRemoteId &&
        first.roomRemoteId == second.roomRemoteId &&
        first.propertyRemoteId.orEmpty() == second.propertyRemoteId.orEmpty() &&
        first.businessDateMillis == second.businessDateMillis &&
        first.grossAmount == second.grossAmount &&
        first.taxableAmount == second.taxableAmount &&
        first.gstRatePercent == second.gstRatePercent &&
        first.gstAmount == second.gstAmount &&
        first.hsnSacCode.orEmpty() == second.hsnSacCode.orEmpty() &&
        first.slabRemoteId.orEmpty() == second.slabRemoteId.orEmpty() &&
        first.slabName.orEmpty() == second.slabName.orEmpty() &&
        first.cgstRatePercent == second.cgstRatePercent &&
        first.sgstRatePercent == second.sgstRatePercent &&
        first.cessRatePercent == second.cessRatePercent &&
        first.cgstAmount == second.cgstAmount &&
        first.sgstAmount == second.sgstAmount &&
        first.cessAmount == second.cessAmount &&
        first.source == second.source &&
        first.isDeleted == second.isDeleted
}

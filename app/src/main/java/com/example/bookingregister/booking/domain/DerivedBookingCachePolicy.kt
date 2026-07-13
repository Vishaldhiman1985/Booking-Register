package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity

/**
 * Applies locally derived display/accounting caches without turning them into an
 * authoritative booking edit. Revision ownership and sync intent stay with the
 * original booking and its explicit outbox operation.
 */
object DerivedBookingCachePolicy {
    fun preserveSyncIdentity(
        original: BookingEntity,
        recalculated: BookingEntity
    ): BookingEntity = recalculated.copy(
        localId = original.localId,
        updatedAt = original.updatedAt,
        syncState = original.syncState,
        lastSyncError = original.lastSyncError,
        lastSyncedAt = original.lastSyncedAt,
        revision = original.revision,
        baseRevision = original.baseRevision,
        updatedByUid = original.updatedByUid
    )
}

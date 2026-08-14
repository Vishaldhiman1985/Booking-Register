package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingOrphanReconciliationPolicyTest {
    @Test
    fun `matching cloud echo safely clears a false orphan`() {
        val cloud = booking()
        val local = cloud.copy(
            syncState = SyncState.FAILED,
            lastSyncError = "[ORPHANED_BOOKING_INTENT] Booking sync intent is missing.",
            revision = 4,
            updatedAt = 200
        )

        assertTrue(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = false
            )
        )
    }

    @Test
    fun `different receptionist data remains preserved for review`() {
        val cloud = booking()
        val local = cloud.copy(
            notes = "Important local note",
            syncState = SyncState.FAILED,
            lastSyncError = "[ORPHANED_BOOKING_INTENT] Booking sync intent is missing."
        )

        assertFalse(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = false
            )
        )
    }

    @Test
    fun `different room assignment remains preserved for review`() {
        val cloud = booking()
        val local = cloud.copy(
            roomRemoteIds = listOf("room-2"),
            syncState = SyncState.FAILED,
            lastSyncError = "[ORPHANED_BOOKING_INTENT] Booking sync intent is missing."
        )

        assertFalse(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = false
            )
        )
    }

    @Test
    fun `derived payment cache difference does not impersonate a receptionist edit`() {
        val cloud = booking()
        val local = cloud.copy(
            paid = 500.0,
            balance = 500.0,
            syncState = SyncState.FAILED,
            lastSyncError = "[ORPHANED_BOOKING_INTENT] Booking sync intent is missing."
        )

        assertTrue(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = false
            )
        )
    }

    @Test
    fun `unrelated failure is never auto reconciled`() {
        val cloud = booking()
        val local = cloud.copy(
            syncState = SyncState.FAILED,
            lastSyncError = "[FAILED_PRECONDITION] Room details are locked."
        )

        assertFalse(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = false
            )
        )
    }

    @Test
    fun `a durable pending command always wins over cloud reconciliation`() {
        val cloud = booking()
        val local = cloud.copy(
            syncState = SyncState.FAILED,
            lastSyncError = "[ORPHANED_BOOKING_INTENT] Booking sync intent is missing."
        )

        assertFalse(
            BookingOrphanReconciliationPolicy.canAcceptMatchingCloudBooking(
                local, cloud, hasPendingIntent = true
            )
        )
    }

    private fun booking() = BookingEntity(
        remoteId = "booking-1",
        bookingUuid = "booking-1",
        hotelRemoteId = "hotel-1",
        guestName = "Guest",
        checkInMillis = 1_000,
        checkOutMillis = 2_000,
        roomRemoteIds = listOf("room-1"),
        notes = "Cloud note",
        revision = 5,
        updatedAt = 300
    )
}

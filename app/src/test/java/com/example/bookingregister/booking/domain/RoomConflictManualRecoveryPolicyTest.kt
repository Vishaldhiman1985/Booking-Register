package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomConflictManualRecoveryPolicyTest {

    @Test
    fun `proven rejected local create may be manually removed`() {
        assertTrue(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = localUnsyncedBooking(),
                serverBookingExists = false,
                hasRejectedCreateAudit = true,
                hasServerBusinessHistory = false,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = true
            )
        )
    }

    @Test
    fun `real server booking can never be removed as rejected local copy`() {
        assertFalse(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = localUnsyncedBooking(),
                serverBookingExists = true,
                hasRejectedCreateAudit = true,
                hasServerBusinessHistory = false,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = true
            )
        )
    }

    @Test
    fun `missing rejected create audit blocks manual removal`() {
        assertFalse(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = localUnsyncedBooking(),
                serverBookingExists = false,
                hasRejectedCreateAudit = false,
                hasServerBusinessHistory = false,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = true
            )
        )
    }

    @Test
    fun `business history blocks manual removal`() {
        assertFalse(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = localUnsyncedBooking(),
                serverBookingExists = false,
                hasRejectedCreateAudit = true,
                hasServerBusinessHistory = true,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = true
            )
        )
    }

    @Test
    fun `previous server acceptance blocks manual removal`() {
        val previouslySynced = localUnsyncedBooking().copy(
            revision = 1L,
            baseRevision = 1L,
            lastSyncedAt = 123L
        )

        assertFalse(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = previouslySynced,
                serverBookingExists = false,
                hasRejectedCreateAudit = true,
                hasServerBusinessHistory = false,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = true
            )
        )
    }

    @Test
    fun `unrelated pending save blocks manual removal`() {
        assertFalse(
            RoomConflictResolutionPolicy.canDiscardRejectedCreateOperationsForManualRecovery(
                booking = localUnsyncedBooking(),
                serverBookingExists = false,
                hasRejectedCreateAudit = true,
                hasServerBusinessHistory = false,
                hasPendingOperations = true,
                allPendingOperationsAreRejectedRoomConflicts = false
            )
        )
    }

    private fun localUnsyncedBooking() = BookingEntity(
        remoteId = "rejected-local-booking",
        bookingUuid = "rejected-local-booking",
        hotelRemoteId = "hotel-a",
        propertyRemoteId = "property-a",
        guestName = "Test Guest",
        checkInMillis = 1_700_000_000_000L,
        checkOutMillis = 1_700_086_400_000L,
        roomRemoteIds = listOf("room-101"),
        revision = 0L,
        baseRevision = 0L,
        lastSyncedAt = null
    )
}

package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DerivedBookingCachePolicyTest {
    @Test
    fun `remote hydration cache refresh stays synced and preserves revision identity`() {
        val original = booking(syncState = SyncState.SYNCED, revision = 1, updatedAt = 100)
        val result = DerivedBookingCachePolicy.preserveSyncIdentity(
            original,
            original.copy(paid = 500.0, balance = 2_300.0, paymentStatus = "PARTIALLY_PAID", updatedAt = 999)
        )

        assertEquals(500.0, result.paid, 0.001)
        assertEquals(2_300.0, result.balance, 0.001)
        assertEquals(SyncState.SYNCED, result.syncState)
        assertEquals(1, result.revision)
        assertEquals(100, result.updatedAt)
    }

    @Test
    fun `genuine pending local edit remains protected while cache is recalculated`() {
        val original = booking(syncState = SyncState.PENDING, revision = 1, updatedAt = 200)
            .copy(roomRemoteIds = listOf("room-a", "room-b"), baseRevision = 1)
        val result = DerivedBookingCachePolicy.preserveSyncIdentity(
            original,
            original.copy(paid = 500.0, balance = 2_300.0)
        )

        assertEquals(listOf("room-a", "room-b"), result.roomRemoteIds)
        assertEquals(SyncState.PENDING, result.syncState)
        assertEquals(1, result.baseRevision)
        assertEquals(200, result.updatedAt)
    }

    private fun booking(syncState: String, revision: Long, updatedAt: Long) = BookingEntity(
        remoteId = "booking-a",
        bookingUuid = "booking-a",
        hotelRemoteId = "hotel-a",
        guestName = "Guest",
        checkInMillis = 1_000,
        checkOutMillis = 2_000,
        roomRemoteIds = listOf("room-a"),
        grossCharges = 2_800.0,
        receivable = 2_800.0,
        balance = 2_800.0,
        updatedAt = updatedAt,
        syncState = syncState,
        revision = revision,
        baseRevision = revision
    )
}

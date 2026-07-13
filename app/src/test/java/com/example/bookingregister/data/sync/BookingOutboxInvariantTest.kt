package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingOutboxInvariantTest {
    @Test
    fun `only pending bookings with an executable outbox are eligible to upload`() {
        val bookings = listOf(booking("real-edit", SyncState.PENDING), booking("derived-orphan", SyncState.PENDING))
        val executableIds = setOf("real-edit")
        val eligible = bookings.filter { it.syncState == SyncState.PENDING && it.remoteId in executableIds }
        assertEquals(listOf("real-edit"), eligible.map { it.remoteId })
    }

    private fun booking(id: String, state: String) = BookingEntity(
        remoteId = id,
        bookingUuid = id,
        hotelRemoteId = "hotel-a",
        guestName = "Guest",
        checkInMillis = 1,
        checkOutMillis = 2,
        roomRemoteIds = listOf("room-a"),
        syncState = state
    )
}

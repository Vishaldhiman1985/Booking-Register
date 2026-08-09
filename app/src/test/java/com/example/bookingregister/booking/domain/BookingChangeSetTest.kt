package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingChangeSetTest {
    @Test
    fun `one save contains only changed price and added room`() {
        val previous = booking(3_000.0, listOf("H101"))
        val requested = booking(3_200.0, listOf("H101", "H102"))
        val changeSet = BookingChangeSet.create(previous, requested, emptyList(), emptyList())

        assertEquals(mapOf("grossCharges" to 3_200.0), changeSet.setFields)
        assertEquals(listOf("H102"), changeSet.addRoomRemoteIds)
        assertTrue(changeSet.removeRoomRemoteIds.isEmpty())
        assertTrue(changeSet.rebuildFinancialLines)
    }

    @Test
    fun `json round trip preserves durable offline command`() {
        val changeSet = BookingChangeSet.create(
            booking(3_000.0, listOf("H101")),
            booking(3_200.0, listOf("H101", "H102")),
            emptyList(), emptyList()
        )
        val decoded = BookingChangeSet.fromJson(changeSet.toJson())
        assertEquals(changeSet.bookingRemoteId, decoded.bookingRemoteId)
        assertEquals(changeSet.addRoomRemoteIds, decoded.addRoomRemoteIds)
        assertEquals(3_200.0, (decoded.setFields["grossCharges"] as Number).toDouble(), 0.001)
        assertFalse(decoded.create)
    }

    @Test
    fun `cancellation settlement decision is synced without rebuilding room charges`() {
        val previous = booking(3_000.0, listOf("H101"))
        val requested = previous.copy(
            bookingStatus = BookingStatus.CANCELLED,
            cancelledAt = 4_000,
            cancellationReason = "Guest cancelled",
            cancellationSettlementStatus = CancellationSettlementStatus.DECIDED,
            cancellationSettlementOutcome = CancellationSettlementOutcome.PARTIAL_REFUND,
            cancellationApprovedRefundAmount = 100.0,
            cancellationFeeAmount = 100.0,
            cancellationRefundBaselineAmount = 0.0,
            cancellationDecisionAt = 4_000
        )

        val changeSet = BookingChangeSet.create(previous, requested, emptyList(), emptyList())

        assertEquals(BookingStatus.CANCELLED, changeSet.setFields["bookingStatus"])
        assertEquals(CancellationSettlementStatus.DECIDED, changeSet.setFields["cancellationSettlementStatus"])
        assertEquals(100.0, changeSet.setFields["cancellationApprovedRefundAmount"])
        assertFalse(changeSet.rebuildFinancialLines)
    }

    @Test
    fun `unchanged booking does not create an empty sync command`() {
        val existing = booking(3_000.0, listOf("H101"))

        val changeSet = BookingChangeSet.create(existing, existing, emptyList(), emptyList())

        assertFalse(changeSet.hasChanges)
    }

    private fun booking(total: Double, rooms: List<String>) = BookingEntity(
        remoteId = "booking-a", bookingUuid = "booking-a", hotelRemoteId = "hotel-a",
        guestName = "Guest", checkInMillis = 1_000, checkOutMillis = 2_000,
        roomRemoteIds = rooms, grossCharges = total, rate = total, receivable = total
    )
}

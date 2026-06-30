package com.example.bookingregister.revenue.domain

import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.folio.domain.MiniFolioBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RevenueLedgerBuilderTest {
    private val folioBuilder = MiniFolioBuilder()
    private val builder = RevenueLedgerBuilder()
    private val calculator = RevenueCalculator()

    @Test
    fun splitsRoomRevenueAcrossStayNightsAndRooms() {
        val room101 = room("room_101")
        val room102 = room("room_102")
        val booking = booking(
            checkInMillis = day(2026, Calendar.MAY, 12),
            checkOutMillis = day(2026, Calendar.MAY, 15),
            roomRemoteIds = listOf(room101.remoteId, room102.remoteId),
            receivable = 6000.0,
            paid = 4000.0,
            updatedAt = day(2026, Calendar.MAY, 10)
        )

        val entries = builder.build(listOf(room101, room102), listOf(booking))
        val roomRevenueEntries = entries.filter { it.type == RevenueLedgerEntryType.ROOM_REVENUE }
        val folio = folioBuilder.build(listOf(room101, room102), listOf(booking)).single()

        assertEquals(6000.0, folio.totalCharges, 0.001)
        assertEquals(4000.0, folio.totalPayments, 0.001)
        assertEquals(2000.0, folio.balance, 0.001)
        assertEquals(6, roomRevenueEntries.size)
        assertEquals(6000.0, roomRevenueEntries.sumOf { it.amount }, 0.001)
        assertEquals(4000.0, entries.sumOfType(RevenueLedgerEntryType.PAYMENT_RECEIVED), 0.001)
    }

    @Test
    fun calculatesRevenueForOnlySelectedRange() {
        val room101 = room("room_101")
        val booking = booking(
            checkInMillis = day(2026, Calendar.MAY, 12),
            checkOutMillis = day(2026, Calendar.MAY, 15),
            roomRemoteIds = listOf(room101.remoteId),
            receivable = 3000.0,
            paid = 0.0
        )

        val entries = builder.build(listOf(room101), listOf(booking))
        val summary = calculator.summarize(
            entries,
            DateRange(
                startMillis = day(2026, Calendar.MAY, 13),
                endMillis = day(2026, Calendar.MAY, 15)
            )
        )

        assertEquals(2000.0, summary.roomRevenue, 0.001)
        assertEquals(2000.0, summary.pendingBalance, 0.001)
    }

    private fun room(remoteId: String): RoomEntity {
        return RoomEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel_test",
            roomName = remoteId
        )
    }

    private fun booking(
        checkInMillis: Long,
        checkOutMillis: Long,
        roomRemoteIds: List<String>,
        receivable: Double,
        paid: Double,
        updatedAt: Long = checkInMillis
    ): BookingEntity {
        return BookingEntity(
            remoteId = "booking_1",
            bookingUuid = "booking_1",
            hotelRemoteId = "hotel_test",
            guestName = "Ram",
            checkInMillis = checkInMillis,
            checkOutMillis = checkOutMillis,
            roomRemoteIds = roomRemoteIds,
            receivable = receivable,
            paid = paid,
            updatedAt = updatedAt
        )
    }

    private fun day(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }

    private fun List<RevenueLedgerEntry>.sumOfType(type: String): Double {
        return filter { it.type == type }.sumOf { it.amount }
    }
}

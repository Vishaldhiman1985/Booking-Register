package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.bookingregister.authoritativeRoomLines

class OtaScenarioTest {

    @Test
    fun otaBookingKeepsRoomFolioBalanceAgainstGrossStayTotal() {
        val booking = BookingEntity(
            remoteId = "booking_ota",
            bookingUuid = "BK-OTA",
            hotelRemoteId = "hotel_1",
            guestName = "OTA Guest",
            checkInMillis = 0L,
            checkOutMillis = 86_400_000L,
            roomRemoteIds = listOf("room_1"),
            sourceType = BookingSourceType.OTA,
            grossCharges = 6_000.0,
            receivable = 6_000.0,
            expectedPayout = 5_200.0,
            rate = 6_000.0
        )

        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = emptyList(),
            foodOrders = emptyList(),
            bookingFinancialLines = authoritativeRoomLines(booking)
        )

        assertEquals(6_000.0, summary.stayTotal, 0.01)
        assertEquals(0.0, summary.stayPaid, 0.01)
        assertEquals(6_000.0, summary.stayBalance, 0.01)
        assertEquals(6_000.0, summary.grandBalance, 0.01)
    }
}

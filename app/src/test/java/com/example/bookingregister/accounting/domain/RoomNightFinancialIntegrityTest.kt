package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingFinancialLineSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomNightFinancialIntegrityTest {
    private val day = 86_400_000L

    @Test fun requiresExactlyOneLineForEveryRoomNight() {
        val booking = booking(rooms = listOf("101", "102"), nights = 2)
        val complete = RoomNightFinancialIntegrity.expectedKeys(booking).mapIndexed { index, key ->
            line("line_$index", booking, key.first, key.second)
        }
        assertTrue(RoomNightFinancialIntegrity.validate(booking, complete).isValid)
        assertFalse(RoomNightFinancialIntegrity.validate(booking, complete.dropLast(1)).isValid)
        assertFalse(RoomNightFinancialIntegrity.validate(booking, complete + complete.first().copy(remoteId = "duplicate")).isValid)
    }

    @Test fun zeroValueComplimentaryLinesAreValidButMissingLinesAreNot() {
        val booking = booking(rooms = listOf("101"), nights = 1)
        val zeroLine = line("zero", booking, "101", booking.checkInMillis, gross = 0.0)
        assertTrue(RoomNightFinancialIntegrity.validate(booking, listOf(zeroLine)).isValid)
        assertFalse(RoomNightFinancialIntegrity.validate(booking, emptyList()).isValid)
    }

    @Test fun otaNetPayoutMayDifferFromGuestTaxInclusivePrice() {
        val booking = booking(rooms = listOf("101"), nights = 1)
        val ota = line("ota", booking, "101", booking.checkInMillis, gross = 1_692.95).copy(
            taxableAmount = 2_095.24,
            gstAmount = 104.76,
            source = BookingFinancialLineSource.OTA_IMPORT
        )
        assertTrue(RoomNightFinancialIntegrity.validate(booking, listOf(ota)).isValid)
    }

    private fun booking(rooms: List<String>, nights: Int) = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BR-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 1_700_006_400_000L,
        checkOutMillis = 1_700_006_400_000L + nights * day,
        roomRemoteIds = rooms
    )

    private fun line(
        id: String,
        booking: BookingEntity,
        room: String,
        date: Long,
        gross: Double = 1_050.0
    ) = BookingFinancialLineEntity(
        remoteId = id,
        hotelRemoteId = booking.hotelRemoteId,
        bookingRemoteId = booking.remoteId,
        roomRemoteId = room,
        businessDateMillis = date,
        grossAmount = gross,
        taxableAmount = if (gross == 0.0) 0.0 else 1_000.0,
        gstAmount = if (gross == 0.0) 0.0 else 50.0
    )
}

package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyRevenueReportEngineTest {

    private val engine = PropertyRevenueReportEngine()
    private val day = 86_400_000L

    @Test
    fun build_keepsRevenueStrictlyInsideSelectedPropertyAndPeriod() {
        val propertyA = "property_a"
        val propertyB = "property_b"

        val bookingA = BookingEntity(
            remoteId = "booking_a",
            bookingUuid = "uuid_a",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyA,
            guestName = "A",
            checkInMillis = day,
            checkOutMillis = 3 * day,
            roomRemoteIds = listOf("room_a"),
            bookingStatus = BookingStatus.RESERVED
        )

        val bookingB = BookingEntity(
            remoteId = "booking_b",
            bookingUuid = "uuid_b",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyB,
            guestName = "B",
            checkInMillis = day,
            checkOutMillis = 3 * day,
            roomRemoteIds = listOf("room_b"),
            bookingStatus = BookingStatus.RESERVED
        )

        val raw = PropertyReportRawData(
            properties = emptyList(),
            rooms = listOf(
                RoomEntity(
                    remoteId = "room_a",
                    hotelRemoteId = "hotel_1",
                    roomName = "A1",
                    propertyRemoteId = propertyA
                ),
                RoomEntity(
                    remoteId = "room_b",
                    hotelRemoteId = "hotel_1",
                    roomName = "B1",
                    propertyRemoteId = propertyB
                )
            ),
            bookings = listOf(bookingA, bookingB),
            financialLines = listOf(
                BookingFinancialLineEntity(
                    remoteId = "line_a_1",
                    hotelRemoteId = "hotel_1",
                    bookingRemoteId = bookingA.remoteId,
                    roomRemoteId = "room_a",
                    propertyRemoteId = propertyA,
                    businessDateMillis = day,
                    grossAmount = 1_120.0,
                    taxableAmount = 1_000.0,
                    gstAmount = 120.0,
                    cgstAmount = 60.0,
                    sgstAmount = 60.0
                ),
                BookingFinancialLineEntity(
                    remoteId = "line_a_2",
                    hotelRemoteId = "hotel_1",
                    bookingRemoteId = bookingA.remoteId,
                    roomRemoteId = "room_a",
                    propertyRemoteId = propertyA,
                    businessDateMillis = 5 * day,
                    grossAmount = 2_240.0,
                    taxableAmount = 2_000.0,
                    gstAmount = 240.0,
                    cgstAmount = 120.0,
                    sgstAmount = 120.0
                ),
                BookingFinancialLineEntity(
                    remoteId = "line_b_1",
                    hotelRemoteId = "hotel_1",
                    bookingRemoteId = bookingB.remoteId,
                    roomRemoteId = "room_b",
                    propertyRemoteId = propertyB,
                    businessDateMillis = day,
                    grossAmount = 5_600.0,
                    taxableAmount = 5_000.0,
                    gstAmount = 600.0,
                    cgstAmount = 300.0,
                    sgstAmount = 300.0
                )
            ),
            payments = listOf(
                BookingPaymentEntity(
                    remoteId = "payment_a",
                    hotelRemoteId = "hotel_1",
                    bookingRemoteId = bookingA.remoteId,
                    paymentType = "PAYMENT",
                    amount = 1_120.0,
                    paymentMillis = day
                ),
                BookingPaymentEntity(
                    remoteId = "payment_b",
                    hotelRemoteId = "hotel_1",
                    bookingRemoteId = bookingB.remoteId,
                    paymentType = "PAYMENT",
                    amount = 5_600.0,
                    paymentMillis = day
                )
            )
        )

        val result = engine.build(
            raw = raw,
            scope = PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = propertyA,
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 3 * day
            )
        )

        assertEquals(1_120.0, result.grossRoomBilling, 0.001)
        assertEquals(1_000.0, result.roomRevenueExGst, 0.001)
        assertEquals(120.0, result.gstCollected, 0.001)
        assertEquals(1_120.0, result.paymentsRecordedInPeriod, 0.001)
    }
}

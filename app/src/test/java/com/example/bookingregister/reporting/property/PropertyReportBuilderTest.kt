package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyReportBuilderTest {
    private val builder = PropertyReportBuilder()
    private val day = 86_400_000L

    @Test
    fun propertyScope_neverLeaksAnotherPropertyRecords() {
        val propertyA = "property_a"
        val propertyB = "property_b"
        val bookingA = booking("booking_a", propertyA, "room_a")
        val bookingB = booking("booking_b", propertyB, "room_b")

        val raw = PropertyReportRawData(
            properties = emptyList(),
            rooms = listOf(
                room("room_a", propertyA),
                room("room_b", propertyB)
            ),
            bookings = listOf(bookingA, bookingB),
            financialLines = listOf(
                line("line_a", bookingA.remoteId, "room_a", propertyA, 1 * day, 1_120.0, 1_000.0, 120.0),
                line("line_b", bookingB.remoteId, "room_b", propertyB, 1 * day, 2_240.0, 2_000.0, 240.0)
            ),
            payments = listOf(
                payment("payment_a", bookingA.remoteId, 1 * day, 1_120.0),
                payment("payment_b", bookingB.remoteId, 1 * day, 2_240.0)
            )
        )

        val dataset = builder.scope(
            raw,
            PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = propertyA,
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 10 * day
            )
        )

        assertEquals(listOf("room_a"), dataset.rooms.map { it.remoteId })
        assertEquals(listOf("booking_a"), dataset.bookings.map { it.remoteId })
        assertEquals(listOf("line_a"), dataset.financialLines.map { it.remoteId })
        assertEquals(listOf("payment_a"), dataset.payments.map { it.remoteId })
        assertFalse(dataset.bookings.any { it.propertyRemoteId == propertyB })
    }

    @Test
    fun revenueFacts_useFinancialLinesAndPassStoredOtaSettlementWithoutRecalculation() {
        val propertyA = "property_a"
        val booking = booking("booking_a", propertyA, "room_a").copy(
            roomRevenue = 99_999.0,
            grossCharges = 99_999.0,
            propertyTax = 0.0,
            commissionAmount = 150.0,
            commissionTax = 27.0,
            sourceFee = 20.0,
            tdsAmount = 10.0,
            tcsAmount = 10.0,
            expectedPayout = 903.0,
            sourceType = BookingSourceType.OTA
        )
        val raw = PropertyReportRawData(
            properties = emptyList(),
            rooms = listOf(room("room_a", propertyA)),
            bookings = listOf(booking),
            financialLines = listOf(
                line("line_1", booking.remoteId, "room_a", propertyA, 1 * day, 1_120.0, 1_000.0, 120.0),
                line("line_2", booking.remoteId, "room_a", propertyA, 2 * day, 2_240.0, 2_000.0, 240.0)
            ),
            payments = listOf(
                payment("payment_1", booking.remoteId, 2 * day, 500.0),
                payment("refund_1", booking.remoteId, 3 * day, 100.0, BookingPaymentType.REFUND)
            )
        )
        val dataset = builder.scope(
            raw,
            PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = propertyA,
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 10 * day
            )
        )

        val facts = builder.revenueFacts(dataset)

        assertEquals(3_360.0, facts.grossRoomBilling, 0.001)
        assertEquals(3_000.0, facts.roomRevenue, 0.001)
        assertEquals(360.0, facts.gstCollected, 0.001)
        assertEquals(500.0, facts.paymentsRecordedInPeriod, 0.001)
        assertEquals(100.0, facts.refundsRecordedInPeriod, 0.001)

        val settlement = facts.bookingSettlements.single()
        assertEquals(3_000.0, settlement.fullBookingRoomRevenueFromFinancialLines, 0.001)
        assertEquals(150.0, settlement.storedCommissionAmount, 0.001)
        assertEquals(27.0, settlement.storedCommissionTax, 0.001)
        assertEquals(20.0, settlement.storedSourceFee, 0.001)
        assertEquals(10.0, settlement.storedTdsAmount, 0.001)
        assertEquals(10.0, settlement.storedTcsAmount, 0.001)
        assertEquals(903.0, settlement.storedExpectedPayout, 0.001)
        assertTrue(facts.roomRevenue != booking.roomRevenue)
    }

    private fun room(remoteId: String, propertyRemoteId: String) = RoomEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        roomName = remoteId,
        propertyRemoteId = propertyRemoteId
    )

    private fun booking(remoteId: String, propertyRemoteId: String, roomRemoteId: String) = BookingEntity(
        remoteId = remoteId,
        bookingUuid = "uuid_$remoteId",
        hotelRemoteId = "hotel_1",
        propertyRemoteId = propertyRemoteId,
        guestName = "Guest",
        checkInMillis = 1 * day,
        checkOutMillis = 3 * day,
        roomRemoteIds = listOf(roomRemoteId),
        bookingStatus = BookingStatus.RESERVED
    )

    private fun line(
        remoteId: String,
        bookingRemoteId: String,
        roomRemoteId: String,
        propertyRemoteId: String,
        businessDateMillis: Long,
        gross: Double,
        taxable: Double,
        gst: Double
    ) = BookingFinancialLineEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = bookingRemoteId,
        roomRemoteId = roomRemoteId,
        propertyRemoteId = propertyRemoteId,
        businessDateMillis = businessDateMillis,
        grossAmount = gross,
        taxableAmount = taxable,
        gstAmount = gst,
        cgstAmount = gst / 2.0,
        sgstAmount = gst / 2.0
    )

    private fun payment(
        remoteId: String,
        bookingRemoteId: String,
        paymentMillis: Long,
        amount: Double,
        type: String = BookingPaymentType.PAYMENT
    ) = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = bookingRemoteId,
        paymentType = type,
        amount = amount,
        paymentMillis = paymentMillis
    )
}

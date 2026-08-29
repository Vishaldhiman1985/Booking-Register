package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyBalanceReportBuilderTest {
    private val builder = PropertyBalanceReportBuilder()

    @Test
    fun balanceUsesActualPaymentRefundAndCorrectionRowsInsteadOfPaidCache() {
        val direct = booking(
            remoteId = "direct_1",
            sourceType = BookingSourceType.DIRECT,
            receivable = 1_000.0,
            expectedPayout = 0.0
        ).copy(
            paid = 99_999.0,
            balance = 0.0
        )
        val ota = booking(
            remoteId = "ota_1",
            sourceType = BookingSourceType.OTA,
            receivable = 1_200.0,
            expectedPayout = 900.0
        ).copy(
            paid = 99_999.0,
            balance = 0.0
        )

        val dataset = PropertyReportDataset(
            scope = PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = "property_a",
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 10_000
            ),
            properties = emptyList(),
            rooms = emptyList(),
            bookings = listOf(direct, ota),
            financialLines = emptyList(),
            payments = listOf(
                payment("direct_pay", direct.remoteId, 400.0, BookingPaymentType.PAYMENT),
                payment("direct_refund", direct.remoteId, 100.0, BookingPaymentType.REFUND),
                payment("ota_pay", ota.remoteId, 500.0, BookingPaymentType.PAYMENT),
                payment("ota_correction", ota.remoteId, 100.0, BookingPaymentType.ADJUSTMENT)
            )
        )

        val facts = builder.build(dataset)

        assertEquals(1_900.0, facts.totalReceivable, 0.001)
        assertEquals(700.0, facts.totalReceived, 0.001)
        assertEquals(700.0, facts.totalAppliedReceived, 0.001)
        assertEquals(0.0, facts.totalExcessPayment, 0.001)
        assertEquals(1_200.0, facts.totalOutstanding, 0.001)
        assertEquals(500.0, facts.otaOutstanding, 0.001)
        assertEquals(700.0, facts.guestOutstanding, 0.001)
        assertEquals(2, facts.openBookingCount)

        val directRow = facts.bookings.single { it.bookingRemoteId == direct.remoteId }
        assertEquals(300.0, directRow.received, 0.001)
        assertEquals(300.0, directRow.appliedReceived, 0.001)
        assertEquals(0.0, directRow.excessPayment, 0.001)
        assertEquals(700.0, directRow.outstanding, 0.001)

        val otaRow = facts.bookings.single { it.bookingRemoteId == ota.remoteId }
        assertEquals(900.0, otaRow.receivable, 0.001)
        assertEquals(400.0, otaRow.received, 0.001)
        assertEquals(400.0, otaRow.appliedReceived, 0.001)
        assertEquals(0.0, otaRow.excessPayment, 0.001)
        assertEquals(500.0, otaRow.outstanding, 0.001)
    }

    @Test
    fun excessPaymentIsSeparatedAndDoesNotReduceAnotherBookingsOutstanding() {
        val subham = booking(
            remoteId = "subham",
            sourceType = BookingSourceType.DIRECT,
            receivable = 10_500.0,
            expectedPayout = 0.0
        )
        val keshav = booking(
            remoteId = "keshav",
            sourceType = BookingSourceType.DIRECT,
            receivable = 7_000.0,
            expectedPayout = 0.0
        )
        val unpaidGuest = booking(
            remoteId = "unpaid",
            sourceType = BookingSourceType.DIRECT,
            receivable = 30_000.0,
            expectedPayout = 0.0
        )

        val dataset = PropertyReportDataset(
            scope = PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = "property_a",
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 10_000
            ),
            properties = emptyList(),
            rooms = emptyList(),
            bookings = listOf(subham, keshav, unpaidGuest),
            financialLines = emptyList(),
            payments = listOf(
                payment("subham_pay", subham.remoteId, 23_500.0, BookingPaymentType.PAYMENT),
                payment("keshav_pay", keshav.remoteId, 14_000.0, BookingPaymentType.PAYMENT)
            )
        )

        val facts = builder.build(dataset)

        assertEquals(47_500.0, facts.totalReceivable, 0.001)
        assertEquals(37_500.0, facts.totalReceived, 0.001)
        assertEquals(17_500.0, facts.totalAppliedReceived, 0.001)
        assertEquals(20_000.0, facts.totalExcessPayment, 0.001)
        assertEquals(30_000.0, facts.totalOutstanding, 0.001)
        assertEquals(30_000.0, facts.guestOutstanding, 0.001)
        assertEquals(0.0, facts.otaOutstanding, 0.001)
        assertEquals(1, facts.openBookingCount)

        val subhamRow = facts.bookings.single { it.bookingRemoteId == subham.remoteId }
        assertEquals(10_500.0, subhamRow.appliedReceived, 0.001)
        assertEquals(13_000.0, subhamRow.excessPayment, 0.001)
        assertEquals(0.0, subhamRow.outstanding, 0.001)

        val keshavRow = facts.bookings.single { it.bookingRemoteId == keshav.remoteId }
        assertEquals(7_000.0, keshavRow.appliedReceived, 0.001)
        assertEquals(7_000.0, keshavRow.excessPayment, 0.001)
        assertEquals(0.0, keshavRow.outstanding, 0.001)

        val unpaidRow = facts.bookings.single { it.bookingRemoteId == unpaidGuest.remoteId }
        assertEquals(0.0, unpaidRow.appliedReceived, 0.001)
        assertEquals(0.0, unpaidRow.excessPayment, 0.001)
        assertEquals(30_000.0, unpaidRow.outstanding, 0.001)

        assertEquals(
            facts.totalReceived,
            facts.totalAppliedReceived + facts.totalExcessPayment,
            0.001
        )
        assertEquals(
            facts.totalOutstanding,
            facts.totalReceivable - facts.totalAppliedReceived,
            0.001
        )
    }

    private fun booking(
        remoteId: String,
        sourceType: String,
        receivable: Double,
        expectedPayout: Double
    ) = BookingEntity(
        remoteId = remoteId,
        bookingUuid = "uuid_$remoteId",
        hotelRemoteId = "hotel_1",
        propertyRemoteId = "property_a",
        guestName = "Guest $remoteId",
        sourceType = sourceType,
        checkInMillis = 0,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        receivable = receivable,
        rate = receivable,
        expectedPayout = expectedPayout,
        bookingStatus = BookingStatus.RESERVED
    )

    private fun payment(
        remoteId: String,
        bookingRemoteId: String,
        amount: Double,
        type: String
    ) = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = bookingRemoteId,
        paymentType = type,
        amount = amount,
        paymentMillis = 1
    )
}

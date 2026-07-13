package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.booking.domain.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniFolioBuilderTest {
    private val builder = MiniFolioBuilder()

    @Test
    fun missingRoomLinesAreReportedAndNeverMarkedSettled() {
        val folio = requireNotNull(
            builder.buildForBooking(
                booking = booking(),
                activeRoomIds = setOf("room_1")
            )
        )

        assertEquals(MiniFolioStatus.INTEGRITY_ERROR, folio.status)
        assertTrue(folio.integrityErrors.any { it.contains("Missing room-night") })
    }

    @Test
    fun allocatedCorrectionUsesCorrectionDescription() {
        val folio = builder.buildForBooking(
            booking = booking(),
            activeRoomIds = setOf("room_1"),
            bookingPayments = listOf(
                payment(
                    remoteId = "correction_1",
                    amount = 1_000.0,
                    type = BookingPaymentType.ADJUSTMENT,
                    stay = 1_000.0,
                    note = "extra paid"
                )
            )
        )

        val paymentLine = requireNotNull(folio).lines.single { it.kind == MiniFolioLineKind.PAYMENT }
        assertEquals(-1_000.0, paymentLine.amount, 0.01)
        assertTrue(paymentLine.description.contains("Payment correction"))
        assertTrue(paymentLine.description.contains("extra paid"))
        assertFalse(paymentLine.description.contains("payment received"))
    }

    @Test
    fun normalAllocatedPaymentKeepsPaymentReceivedDescription() {
        val folio = builder.buildForBooking(
            booking = booking(),
            activeRoomIds = setOf("room_1"),
            bookingPayments = listOf(
                payment(
                    remoteId = "payment_1",
                    amount = 1_000.0,
                    stay = 1_000.0
                )
            )
        )

        val paymentLine = requireNotNull(folio).lines.single { it.kind == MiniFolioLineKind.PAYMENT }
        assertEquals(1_000.0, paymentLine.amount, 0.01)
        assertEquals("Payment received", paymentLine.description)
    }

    @Test
    fun cancelledBookingRemainsARecordButIsExcludedFromRevenueFolios() {
        val cancelled = booking().copy(
            bookingStatus = BookingStatus.CANCELLED,
            cancellationReason = "Guest cancelled"
        )
        val room = RoomEntity(
            remoteId = "room_1",
            hotelRemoteId = "hotel_1",
            roomName = "101"
        )

        assertTrue(builder.build(listOf(room), listOf(cancelled)).isEmpty())
        assertEquals("Guest cancelled", cancelled.cancellationReason)
    }

    private fun booking(): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        receivable = 5_000.0,
        rate = 5_000.0
    )

    private fun payment(
        remoteId: String,
        amount: Double,
        type: String = BookingPaymentType.PAYMENT,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        note: String? = null
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = type,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        allocatedServiceAmount = service,
        note = note,
        paymentMillis = 1L
    )
}

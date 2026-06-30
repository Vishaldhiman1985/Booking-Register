package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.repository.PaymentStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.FolioSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutReadinessPolicyTest {
    @Test
    fun readyWhenCheckedInZeroBalanceAndCheckoutDatePassed() {
        val booking = booking(
            status = BookingStatus.CHECKED_IN,
            checkOutMillis = 1_000L,
            receivable = 5_000.0,
            paid = 5_000.0
        )

        assertTrue(CheckoutReadinessPolicy.isReadyForCheckout(booking, nowMillis = 2_000L))
    }

    @Test
    fun notReadyWhenBalanceIsPending() {
        val booking = booking(
            status = BookingStatus.CHECKED_IN,
            checkOutMillis = 1_000L,
            receivable = 5_000.0,
            paid = 3_000.0
        )

        assertFalse(CheckoutReadinessPolicy.isReadyForCheckout(booking, nowMillis = 2_000L))
    }

    @Test
    fun notReadyBeforeCheckoutDate() {
        val booking = booking(
            status = BookingStatus.CHECKED_IN,
            checkOutMillis = 3_000L,
            receivable = 5_000.0,
            paid = 5_000.0
        )

        assertFalse(CheckoutReadinessPolicy.isReadyForCheckout(booking, nowMillis = 2_000L))
    }

    @Test
    fun notReadyWhenNotCheckedIn() {
        val booking = booking(
            status = BookingStatus.RESERVED,
            checkOutMillis = 1_000L,
            receivable = 5_000.0,
            paid = 5_000.0
        )

        assertFalse(CheckoutReadinessPolicy.isReadyForCheckout(booking, nowMillis = 2_000L))
    }

    @Test
    fun otaReadyIgnoresRoomReceivableWhenFoodAndServiceAreClear() {
        val booking = booking(
            status = BookingStatus.CHECKED_IN,
            checkOutMillis = 1_000L,
            receivable = 12_400.0,
            paid = 0.0,
            sourceType = BookingSourceType.OTA
        )

        assertTrue(
            CheckoutReadinessPolicy.isReadyForCheckout(
                booking = booking,
                summary = summary(stayTotal = 12_400.0),
                nowMillis = 2_000L
            )
        )
    }

    @Test
    fun otaNotReadyWhenFoodOrServiceBalanceIsPending() {
        val booking = booking(
            status = BookingStatus.CHECKED_IN,
            checkOutMillis = 1_000L,
            receivable = 12_400.0,
            paid = 0.0,
            sourceType = BookingSourceType.OTA
        )

        assertFalse(
            CheckoutReadinessPolicy.isReadyForCheckout(
                booking = booking,
                summary = summary(stayTotal = 12_400.0, foodTotal = 160.0),
                nowMillis = 2_000L
            )
        )
    }

    private fun booking(
        status: String,
        checkOutMillis: Long,
        receivable: Double,
        paid: Double,
        sourceType: String = BookingSourceType.DIRECT
    ): BookingEntity {
        return BookingEntity(
            remoteId = "booking_1",
            bookingUuid = "BK-1",
            hotelRemoteId = "hotel_1",
            guestName = "Guest",
            checkInMillis = 0L,
            checkOutMillis = checkOutMillis,
            roomRemoteIds = listOf("room_1"),
            receivable = receivable,
            paid = paid,
            balance = receivable - paid,
            paymentStatus = if (paid >= receivable) PaymentStatus.FULLY_PAID else PaymentStatus.PARTIALLY_PAID,
            bookingStatus = status,
            sourceType = sourceType
        )
    }

    private fun summary(
        stayTotal: Double = 0.0,
        foodTotal: Double = 0.0,
        serviceTotal: Double = 0.0,
        damageTotal: Double = 0.0,
        stayPaid: Double = 0.0,
        foodPaid: Double = 0.0,
        servicePaid: Double = 0.0,
        damagePaid: Double = 0.0
    ): FolioSummary = FolioSummary(
        stayTotal = stayTotal,
        foodTotal = foodTotal,
        serviceTotal = serviceTotal,
        damageTotal = damageTotal,
        stayPaid = stayPaid,
        foodPaid = foodPaid,
        servicePaid = servicePaid,
        damagePaid = damagePaid,
        unappliedPaid = 0.0
    )
}

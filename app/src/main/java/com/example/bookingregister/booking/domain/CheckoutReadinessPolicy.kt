package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.withCalculatedPayment
import com.example.bookingregister.folio.domain.FolioSummary

object CheckoutReadinessPolicy {
    fun isReadyForCheckout(
        booking: BookingEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (booking.isDeleted) return false
        if (booking.bookingStatus != BookingStatus.CHECKED_IN) return false
        if (nowMillis < booking.checkOutMillis) return false

        val normalized = booking.withCalculatedPayment()
        return normalized.balance <= 0.0
    }

    fun isReadyForCheckout(
        booking: BookingEntity,
        summary: FolioSummary,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (booking.isDeleted) return false
        if (booking.bookingStatus != BookingStatus.CHECKED_IN) return false
        if (nowMillis < booking.checkOutMillis) return false

        return CheckoutBalancePolicy.pendingBalanceForCheckout(booking, summary) <= 0.01
    }
}

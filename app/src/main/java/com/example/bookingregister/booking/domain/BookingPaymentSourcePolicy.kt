package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity

/** Keeps payment rows authoritative after a booking has been persisted. */
object BookingPaymentSourcePolicy {
    fun canEditInitialAdvance(existingBooking: BookingEntity?): Boolean {
        return existingBooking == null
    }

    fun authoritativeStayPaid(
        existingBooking: BookingEntity?,
        requestedPaid: Double,
        hasPaymentRows: Boolean,
        stayPaidFromRows: Double
    ): Double {
        return when {
            hasPaymentRows -> stayPaidFromRows
            existingBooking != null -> existingBooking.paid
            else -> requestedPaid
        }.coerceAtLeast(0.0)
    }
}

package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType

object BookingPaymentStatus {
    const val FULLY_PAID = "FULLY_PAID"
    const val PARTIALLY_PAID = "PARTIALLY_PAID"
    const val NOT_PAID = "NOT_PAID"
    const val COMPLIMENTARY = "COMPLIMENTARY"
}

object BookingPaymentCalculator {
    fun normalize(booking: BookingEntity): BookingEntity {
        if (booking.paymentStatus == BookingPaymentStatus.COMPLIMENTARY) {
            return booking.copy(
                rate = 0.0,
                receivable = 0.0,
                paid = 0.0,
                balance = 0.0,
                paymentStatus = BookingPaymentStatus.COMPLIMENTARY
            )
        }

        val total = when {
            booking.sourceType == BookingSourceType.OTA && booking.expectedPayout > 0.0 -> booking.expectedPayout
            booking.receivable > 0.0 -> booking.receivable
            booking.rate > 0.0 -> booking.rate
            else -> 0.0
        }
        val advancePaid = booking.paid.coerceAtLeast(0.0)
        val calculatedBalance = (total - advancePaid).coerceAtLeast(0.0)
        val calculatedStatus = when {
            booking.sourceType == BookingSourceType.OTA -> BookingPaymentStatus.FULLY_PAID
            total > 0.0 && advancePaid >= total -> BookingPaymentStatus.FULLY_PAID
            advancePaid > 0.0 && advancePaid < total -> BookingPaymentStatus.PARTIALLY_PAID
            else -> BookingPaymentStatus.NOT_PAID
        }
        return booking.copy(
            rate = total,
            receivable = total,
            paid = advancePaid,
            balance = calculatedBalance,
            paymentStatus = calculatedStatus
        )
    }
}



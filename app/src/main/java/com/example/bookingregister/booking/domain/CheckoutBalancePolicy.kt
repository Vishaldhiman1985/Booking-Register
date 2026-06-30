package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.FolioSummary

object CheckoutBalancePolicy {
    fun pendingBalanceForCheckout(
        booking: BookingEntity,
        summary: FolioSummary
    ): Double {
        return if (booking.sourceType == BookingSourceType.OTA) {
            summary.foodBalance + summary.serviceBalance + summary.damageBalance
        } else {
            summary.grandBalance
        }.coerceAtLeast(0.0)
    }
}

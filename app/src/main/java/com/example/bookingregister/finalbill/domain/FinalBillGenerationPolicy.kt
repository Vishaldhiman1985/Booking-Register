package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.booking.domain.CheckoutBalancePolicy
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.folio.domain.FolioSummary

object FinalBillGenerationPolicy {
    fun pendingGuestPayableBalance(
        booking: BookingEntity,
        summary: FolioSummary
    ): Double = CheckoutBalancePolicy.pendingBalanceForCheckout(booking, summary)
}

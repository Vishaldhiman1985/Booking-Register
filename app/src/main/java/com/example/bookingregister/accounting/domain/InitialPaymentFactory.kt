package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType

object InitialPaymentFactory {
    fun create(booking: BookingEntity): BookingPaymentEntity? {
        if (booking.paid <= 0.0) return null
        val allocation = PaymentAllocationPolicy.allocate(
            amount = booking.paid,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = booking.grossCharges.coerceAtLeast(0.0))
        )
        return BookingPaymentEntity(
            remoteId = "${booking.remoteId}_payment_initial_paid",
            hotelRemoteId = booking.hotelRemoteId,
            bookingRemoteId = booking.remoteId,
            paymentType = BookingPaymentType.ADVANCE,
            paymentCategory = allocation.selectedCategory,
            amount = booking.paid,
            allocatedStayAmount = allocation.stayAmount,
            allocatedFoodAmount = allocation.foodAmount,
            allocatedServiceAmount = allocation.serviceAmount,
            allocatedDamageAmount = allocation.damageAmount,
            unappliedAmount = allocation.unappliedAmount,
            paymentMillis = booking.updatedAt,
            note = "Initial paid amount",
            updatedAt = booking.updatedAt,
            syncState = SyncState.PENDING
        )
    }
}

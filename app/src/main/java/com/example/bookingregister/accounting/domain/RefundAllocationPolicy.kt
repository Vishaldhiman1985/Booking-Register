package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity

object RefundAllocationPolicy {
    fun remainingRefundable(original: BookingPaymentEntity, alreadyRefunded: Double): Double =
        (original.amount - alreadyRefunded).coerceAtLeast(0.0)

    fun reverse(
        original: BookingPaymentEntity,
        refundAmount: Double,
        alreadyRefunded: Double
    ): PaymentAllocation? {
        if (original.amount <= 0.0 || refundAmount <= 0.0) return null
        if (refundAmount > remainingRefundable(original, alreadyRefunded) + 0.001) return null
        val ratio = refundAmount / original.amount
        return PaymentAllocation(
            selectedCategory = original.paymentCategory,
            stayAmount = original.allocatedStayAmount * ratio,
            foodAmount = original.allocatedFoodAmount * ratio,
            serviceAmount = original.allocatedServiceAmount * ratio,
            damageAmount = original.allocatedDamageAmount * ratio,
            unappliedAmount = original.unappliedAmount * ratio
        )
    }
}

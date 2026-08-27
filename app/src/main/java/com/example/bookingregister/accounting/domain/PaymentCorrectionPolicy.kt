package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import kotlin.math.abs

/**
 * A correction is an audit-safe reversal of one original PAYMENT/ADVANCE entry.
 * To avoid ambiguous partial edits, a correction reverses the full amount that
 * remains after any earlier refunds/corrections linked to that original payment.
 */
object PaymentCorrectionPolicy {
    private const val TOLERANCE = 0.001

    fun alreadyReversed(
        original: BookingPaymentEntity,
        payments: List<BookingPaymentEntity>
    ): Double = payments
        .asSequence()
        .filter { !it.isDeleted }
        .filter { it.originalPaymentRemoteId == original.remoteId }
        .filter { it.paymentType == BookingPaymentType.REFUND || it.paymentType == BookingPaymentType.ADJUSTMENT }
        .sumOf { it.amount }

    fun remainingCorrectable(
        original: BookingPaymentEntity,
        payments: List<BookingPaymentEntity>
    ): Double = (original.amount - alreadyReversed(original, payments)).coerceAtLeast(0.0)

    fun reverseRemaining(
        original: BookingPaymentEntity,
        payments: List<BookingPaymentEntity>,
        correctionAmount: Double
    ): PaymentAllocation? {
        val remaining = remainingCorrectable(original, payments)
        if (original.amount <= TOLERANCE || remaining <= TOLERANCE) return null
        if (abs(correctionAmount - remaining) > TOLERANCE) return null

        val ratio = correctionAmount / original.amount
        val allocatedTotal = original.allocatedStayAmount +
            original.allocatedFoodAmount +
            original.allocatedServiceAmount +
            original.allocatedDamageAmount +
            original.unappliedAmount

        if (allocatedTotal > TOLERANCE) {
            if (allocatedTotal > original.amount + 0.02) return null
            val implicitUnapplied = (original.amount - allocatedTotal).coerceAtLeast(0.0)
            return PaymentAllocation(
                selectedCategory = original.paymentCategory,
                stayAmount = original.allocatedStayAmount * ratio,
                foodAmount = original.allocatedFoodAmount * ratio,
                serviceAmount = original.allocatedServiceAmount * ratio,
                damageAmount = original.allocatedDamageAmount * ratio,
                unappliedAmount = (original.unappliedAmount + implicitUnapplied) * ratio
            )
        }

        // Older payment rows may predate explicit allocation columns. Mirror the
        // folio fallback so their correction reverses the same bucket they currently affect.
        return when (BookingPaymentCategory.normalize(original.paymentCategory)) {
            BookingPaymentCategory.FOOD -> PaymentAllocation(
                selectedCategory = BookingPaymentCategory.FOOD,
                stayAmount = 0.0,
                foodAmount = correctionAmount,
                serviceAmount = 0.0,
                damageAmount = 0.0,
                unappliedAmount = 0.0
            )
            BookingPaymentCategory.SERVICE -> PaymentAllocation(
                selectedCategory = BookingPaymentCategory.SERVICE,
                stayAmount = 0.0,
                foodAmount = 0.0,
                serviceAmount = correctionAmount,
                damageAmount = 0.0,
                unappliedAmount = 0.0
            )
            BookingPaymentCategory.DAMAGE -> PaymentAllocation(
                selectedCategory = BookingPaymentCategory.DAMAGE,
                stayAmount = 0.0,
                foodAmount = 0.0,
                serviceAmount = 0.0,
                damageAmount = correctionAmount,
                unappliedAmount = 0.0
            )
            else -> PaymentAllocation(
                selectedCategory = BookingPaymentCategory.STAY,
                stayAmount = correctionAmount,
                foodAmount = 0.0,
                serviceAmount = 0.0,
                damageAmount = 0.0,
                unappliedAmount = 0.0
            )
        }
    }
}

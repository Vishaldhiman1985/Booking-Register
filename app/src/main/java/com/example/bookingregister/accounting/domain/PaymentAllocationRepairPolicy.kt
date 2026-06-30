package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType

object PaymentAllocationRepairPolicy {
    fun moveAutoStayOverpaymentToFood(
        stayTotal: Double,
        foodTotal: Double,
        payments: List<BookingPaymentEntity>,
        now: Long
    ): List<BookingPaymentEntity> {
        val activePayments = payments.filter { !it.isDeleted }
        val stayPaid = activePayments.sumOf { it.allocatedStayAmount.coerceAtLeast(0.0) }
        val foodPaid = activePayments.sumOf { it.allocatedFoodAmount.coerceAtLeast(0.0) }
        var movableAmount = minOf(
            (stayPaid - stayTotal.coerceAtLeast(0.0)).coerceAtLeast(0.0),
            (foodTotal.coerceAtLeast(0.0) - foodPaid).coerceAtLeast(0.0)
        )
        if (movableAmount <= 0.0) return emptyList()

        return activePayments
            .asReversed()
            .mapNotNull { payment ->
                if (movableAmount <= 0.0 || !payment.canMoveStayAllocationToFood()) return@mapNotNull null
                val move = minOf(payment.allocatedStayAmount, movableAmount)
                movableAmount -= move
                payment.copy(
                    allocatedStayAmount = payment.allocatedStayAmount - move,
                    allocatedFoodAmount = payment.allocatedFoodAmount + move,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = payment.baseRevision.takeIf { it > 0 } ?: payment.revision
                )
            }
    }

    private fun BookingPaymentEntity.canMoveStayAllocationToFood(): Boolean {
        return paymentType == BookingPaymentType.PAYMENT &&
                paymentCategory == BookingPaymentCategory.AUTO &&
                allocatedStayAmount > 0.0 &&
                allocatedFoodAmount == 0.0 &&
                allocatedServiceAmount == 0.0
    }
}
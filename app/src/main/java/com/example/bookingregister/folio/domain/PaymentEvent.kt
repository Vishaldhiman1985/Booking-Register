package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType

object PaymentEventType {
    const val ADVANCE = "ADVANCE"
    const val PAYMENT = "PAYMENT"
    const val REFUND = "REFUND"
    const val ADJUSTMENT = "ADJUSTMENT"
}

data class PaymentEvent(
    val remoteId: String,
    val bookingRemoteId: String,
    val hotelRemoteId: String,
    val type: String,
    val amount: Double,
    val businessDateMillis: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String? = null
)

class PaymentTimelineBuilder {
    fun fromPayments(payments: List<BookingPaymentEntity>): List<PaymentEvent> {
        return payments.filter { !it.isDeleted }.map { payment ->
            PaymentEvent(
                remoteId = payment.remoteId,
                bookingRemoteId = payment.bookingRemoteId,
                hotelRemoteId = payment.hotelRemoteId,
                type = when (payment.paymentType) {
                    BookingPaymentType.ADVANCE -> PaymentEventType.ADVANCE
                    BookingPaymentType.REFUND -> PaymentEventType.REFUND
                    BookingPaymentType.ADJUSTMENT -> PaymentEventType.ADJUSTMENT
                    else -> PaymentEventType.PAYMENT
                },
                amount = payment.amount,
                businessDateMillis = payment.paymentMillis,
                note = payment.note,
                createdAt = payment.updatedAt,
                createdByUid = payment.updatedByUid
            )
        }
    }

    fun paidTotal(events: List<PaymentEvent>): Double {
        return events.sumOf { event ->
            when (event.type) {
                PaymentEventType.REFUND -> -event.amount
                else -> event.amount
            }
        }.coerceAtLeast(0.0)
    }
}

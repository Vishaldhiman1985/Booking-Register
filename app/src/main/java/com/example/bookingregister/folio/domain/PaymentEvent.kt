package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity

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
    fun fromCurrentBookingAggregate(booking: BookingEntity): List<PaymentEvent> {
        if (booking.paid <= 0.0) return emptyList()
        return listOf(
            PaymentEvent(
                remoteId = "${booking.remoteId}_aggregate_paid",
                bookingRemoteId = booking.remoteId,
                hotelRemoteId = booking.hotelRemoteId,
                type = PaymentEventType.PAYMENT,
                amount = booking.paid,
                businessDateMillis = booking.updatedAt.takeIf { it > 0 } ?: booking.checkInMillis,
                note = "Current total paid"
            )
        )
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

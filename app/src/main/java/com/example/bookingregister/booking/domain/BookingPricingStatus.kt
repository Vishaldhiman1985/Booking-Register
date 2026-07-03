package com.example.bookingregister.booking.domain

object BookingPricingStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val COMPLIMENTARY = "COMPLIMENTARY"

    fun normalize(value: String?): String = when (value?.trim()?.uppercase()) {
        PENDING -> PENDING
        COMPLIMENTARY -> COMPLIMENTARY
        else -> CONFIRMED
    }

    fun isPending(value: String?): Boolean = normalize(value) == PENDING

    fun canTakeStayPayment(value: String?): Boolean = !isPending(value)

    fun canGenerateRoomBill(value: String?): Boolean = !isPending(value)
}

package com.example.bookingregister.booking.domain

object BookingStatus {
    const val RESERVED = "RESERVED"
    const val CHECKED_IN = "CHECKED_IN"
    const val CHECKED_OUT = "CHECKED_OUT"

    val ACTIVE_STATUSES = setOf(RESERVED, CHECKED_IN)
}

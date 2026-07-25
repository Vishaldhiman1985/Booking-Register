package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity

object BookingChartVisibilityPolicy {
    fun isVisible(booking: BookingEntity): Boolean =
        !booking.isDeleted && booking.bookingStatus != BookingStatus.CANCELLED

    fun visibleBookings(bookings: List<BookingEntity>): List<BookingEntity> =
        bookings.filter(::isVisible)
}

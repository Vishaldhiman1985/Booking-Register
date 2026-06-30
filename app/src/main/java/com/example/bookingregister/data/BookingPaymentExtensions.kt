package com.example.bookingregister.data

import com.example.bookingregister.booking.domain.BookingPaymentCalculator
import com.example.bookingregister.data.entities.BookingEntity

fun BookingEntity.withCalculatedPayment(): BookingEntity {
    return BookingPaymentCalculator.normalize(this)
}

package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity

/**
 * Unmodified data read from the existing Room database.
 * This model owns no data and performs no writes.
 */
data class PropertyReportRawData(
    val properties: List<ManagedPropertyEntity>,
    val rooms: List<RoomEntity>,
    val bookings: List<BookingEntity>,
    val financialLines: List<BookingFinancialLineEntity>,
    val payments: List<BookingPaymentEntity>
)

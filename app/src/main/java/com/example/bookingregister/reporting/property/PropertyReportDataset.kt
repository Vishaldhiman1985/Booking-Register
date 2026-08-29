package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity

/** Property-isolated read model consumed by reporting only. */
data class PropertyReportDataset(
    val scope: PropertyReportScope,
    val properties: List<ManagedPropertyEntity>,
    val rooms: List<RoomEntity>,
    val bookings: List<BookingEntity>,
    val financialLines: List<BookingFinancialLineEntity>,
    val payments: List<BookingPaymentEntity>
)

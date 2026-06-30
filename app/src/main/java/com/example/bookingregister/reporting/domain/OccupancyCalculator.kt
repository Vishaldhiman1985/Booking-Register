package com.example.bookingregister.reporting.domain

import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity

class OccupancyCalculator {
    fun occupancyPercent(
        rooms: List<RoomEntity>,
        bookings: List<BookingEntity>,
        range: DateRange
    ): Int {
        val activeRoomIds = rooms
            .filter { !it.isDeleted }
            .map { it.remoteId }
            .toSet()
        if (activeRoomIds.isEmpty()) return 0

        val availableRoomNights = activeRoomIds.size * BusinessDates.rangeDays(range)
        if (availableRoomNights <= 0) return 0

        val occupiedRoomNights = bookings
            .filter { !it.isDeleted }
            .sumOf { booking ->
                val overlapNights = BusinessDates.overlapNights(
                    booking.checkInMillis,
                    booking.checkOutMillis,
                    range
                )
                if (overlapNights <= 0) {
                    0
                } else {
                    booking.roomRemoteIds.count { it in activeRoomIds } * overlapNights
                }
            }

        return ((occupiedRoomNights.toDouble() / availableRoomNights) * 100).toInt()
    }
}

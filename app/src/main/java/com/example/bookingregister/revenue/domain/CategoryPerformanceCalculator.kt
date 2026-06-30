package com.example.bookingregister.revenue.domain

import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity

class CategoryPerformanceCalculator {
    fun calculate(
        rooms: List<RoomEntity>,
        bookings: List<BookingEntity>,
        range: DateRange
    ): List<CategoryPerformanceSummary> {
        val activeRooms = rooms.filter { !it.isDeleted }
        val roomsById = activeRooms.associateBy { it.remoteId }
        val rangeDays = BusinessDates.rangeDays(range)
        val totals = activeRooms
            .groupBy { it.categoryName }
            .mapValues { (_, categoryRooms) ->
                CategoryAccumulator(
                    categoryColor = categoryRooms.firstOrNull()?.categoryColor ?: "#6F86A6",
                    availableRoomNights = categoryRooms.size * rangeDays
                )
            }
            .toMutableMap()

        bookings.filter { !it.isDeleted }.forEach { booking ->
            val overlapNights = BusinessDates.overlapNights(
                booking.checkInMillis,
                booking.checkOutMillis,
                range
            )
            if (overlapNights <= 0) return@forEach

            val bookedRooms = booking.roomRemoteIds.mapNotNull { roomsById[it] }
            if (bookedRooms.isEmpty()) return@forEach

            val stayNights = BusinessDates.stayNights(booking.checkInMillis, booking.checkOutMillis)
            val bookedRoomNights = stayNights * bookedRooms.size
            val revenuePerRoomNight = if (bookedRoomNights <= 0) {
                0.0
            } else {
                booking.receivable / bookedRoomNights
            }

            bookedRooms.forEach { room ->
                val accumulator = totals.getOrPut(room.categoryName) {
                    CategoryAccumulator(room.categoryColor, 0)
                }
                accumulator.occupiedRoomNights += overlapNights
                accumulator.revenue += revenuePerRoomNight * overlapNights
            }
        }

        return totals.map { (categoryName, total) ->
            CategoryPerformanceSummary(
                categoryName = categoryName,
                categoryColor = total.categoryColor,
                occupiedRoomNights = total.occupiedRoomNights,
                availableRoomNights = total.availableRoomNights,
                revenue = total.revenue
            )
        }.sortedByDescending { it.revenue }
    }
}

private data class CategoryAccumulator(
    val categoryColor: String,
    val availableRoomNights: Int,
    var occupiedRoomNights: Int = 0,
    var revenue: Double = 0.0
)
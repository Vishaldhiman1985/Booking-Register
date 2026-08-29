package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.reporting.domain.OccupancyCalculator
import com.example.bookingregister.room.domain.RoomLifecyclePolicy
import kotlin.math.roundToInt

/**
 * Read-only property occupancy reporting.
 *
 * Uses the application's existing OccupancyCalculator for chart bucket percentages
 * and mirrors the current RevenueReportActivity room-night rules for summary facts.
 */
class PropertyOccupancyReportBuilder(
    private val occupancyCalculator: OccupancyCalculator = OccupancyCalculator()
) {
    fun build(
        dataset: PropertyReportDataset,
        range: DateRange = DateRange(dataset.scope.startMillis, dataset.scope.endMillis)
    ): PropertyOccupancyFacts {
        val availableRoomNights = dataset.rooms.sumOf {
            RoomLifecyclePolicy.availableNights(it, range)
        }
        val activeRoomIds = dataset.rooms
            .filter { RoomLifecyclePolicy.availableNights(it, range) > 0 }
            .map { it.remoteId }
            .toSet()

        val occupiedRoomNights = if (activeRoomIds.isEmpty()) {
            0
        } else {
            dataset.bookings
                .filter { !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED }
                .sumOf { booking ->
                    BusinessDates.overlapNights(
                        booking.checkInMillis,
                        booking.checkOutMillis,
                        range
                    ) * booking.roomRemoteIds.count { it in activeRoomIds }
                }
        }

        val occupancyPercent = if (availableRoomNights <= 0) {
            0
        } else {
            ((occupiedRoomNights.toDouble() / availableRoomNights) * 100.0).roundToInt()
        }

        return PropertyOccupancyFacts(
            occupancyPercent = occupancyPercent,
            occupiedRoomNights = occupiedRoomNights,
            availableRoomNights = availableRoomNights,
            averageOccupiedRoomsPerDay = occupiedRoomNights.toDouble() /
                BusinessDates.rangeDays(range).coerceAtLeast(1),
            roomCount = dataset.rooms.count { !it.isDeleted }
        )
    }

    /**
     * This is intentionally delegated to the existing app calculator so the
     * annual/monthly/weekly curve bucket values keep the same current behavior.
     */
    fun chartBucketPercent(dataset: PropertyReportDataset, range: DateRange): Int {
        return occupancyCalculator.occupancyPercent(
            rooms = dataset.rooms,
            bookings = dataset.bookings,
            range = range
        )
    }
}

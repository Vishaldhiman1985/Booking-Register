package com.example.bookingregister.room.domain

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange

object RoomLifecycleStatus {
    const val ACTIVE = "ACTIVE"
    const val DISABLED = "DISABLED"
    const val RETIRED = "RETIRED"

    fun normalize(value: String?): String = when (value?.trim()?.uppercase()) {
        DISABLED -> DISABLED
        RETIRED -> RETIRED
        else -> ACTIVE
    }
}

data class RoomHistoryFacts(
    val bookingCount: Int = 0,
    val financialLineCount: Int = 0,
    val foodOrderCount: Int = 0
) {
    val hasAnyHistory: Boolean
        get() = bookingCount > 0 || financialLineCount > 0 || foodOrderCount > 0
}

object RoomLifecyclePolicy {
    fun isBookable(room: RoomEntity): Boolean =
        !room.isDeleted && RoomLifecycleStatus.normalize(room.lifecycleStatus) == RoomLifecycleStatus.ACTIVE

    fun availableNights(room: RoomEntity, range: DateRange): Int {
        if (room.isDeleted) return 0
        val status = RoomLifecycleStatus.normalize(room.lifecycleStatus)
        val unavailableFrom = when (status) {
            RoomLifecycleStatus.RETIRED -> room.retiredAtMillis
            RoomLifecycleStatus.DISABLED -> room.disabledAtMillis
            else -> null
        } ?: return BusinessDates.rangeDays(range)
        val effectiveEnd = minOf(range.endMillis, BusinessDates.startOfDay(unavailableFrom))
        if (effectiveEnd <= range.startMillis) return 0
        return BusinessDates.rangeDays(DateRange(range.startMillis, effectiveEnd))
    }

    fun isVisibleInChartWindow(
        room: RoomEntity,
        bookings: List<BookingEntity>,
        windowStartMillis: Long,
        windowEndMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (room.isDeleted) return false
        if (RoomLifecycleStatus.normalize(room.lifecycleStatus) != RoomLifecycleStatus.RETIRED) return true
        if (windowEndMillis > nowMillis) return false
        return bookings.any { booking ->
            !booking.isDeleted &&
                room.remoteId in booking.roomRemoteIds &&
                booking.checkInMillis < windowEndMillis &&
                booking.checkOutMillis > windowStartMillis
        }
    }

    fun blockingBookings(
        roomRemoteId: String,
        bookings: List<BookingEntity>,
        nowMillis: Long
    ): List<BookingEntity> = bookings.filter { booking ->
        !booking.isDeleted &&
            roomRemoteId in booking.roomRemoteIds &&
            booking.bookingStatus != BookingStatus.CHECKED_OUT &&
            booking.checkOutMillis > nowMillis
    }

    fun deleteError(history: RoomHistoryFacts): String? =
        if (history.hasAnyHistory) {
            "This room has booking or billing history and cannot be deleted. Disable or retire it instead."
        } else null

    fun inactiveTransitionError(
        targetStatus: String,
        reason: String,
        blockingBookings: List<BookingEntity>
    ): String? {
        if (targetStatus !in setOf(RoomLifecycleStatus.DISABLED, RoomLifecycleStatus.RETIRED)) {
            return "Invalid room lifecycle status."
        }
        if (reason.isBlank()) return "A reason is required."
        if (blockingBookings.isNotEmpty()) {
            return "Move, cancel, or check out all current/future bookings before changing this room."
        }
        return null
    }

    fun retirementBillingError(hasUnbilledPastBooking: Boolean): String? =
        if (hasUnbilledPastBooking) {
            "Generate the final bill for all past bookings before retiring this room."
        } else null
}

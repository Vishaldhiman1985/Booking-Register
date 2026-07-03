package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import kotlin.math.abs
import com.example.bookingregister.booking.domain.BookingPricingStatus

data class RoomNightFinancialIntegrityResult(
    val isValid: Boolean,
    val errors: List<String>
)

object RoomNightFinancialIntegrity {
    private const val DAY_MILLIS = 86_400_000L
    private const val MONEY_TOLERANCE = 0.02

    fun validate(
        booking: BookingEntity,
        lines: List<BookingFinancialLineEntity>
    ): RoomNightFinancialIntegrityResult {
        val errors = mutableListOf<String>()
        val active = lines.filter { !it.isDeleted }
        if (BookingPricingStatus.isPending(booking.pricingStatus)) {
            if (active.any { it.bookingRemoteId == booking.remoteId }) {
                errors += "Pending pricing cannot contain room financial lines"
            }
            return RoomNightFinancialIntegrityResult(errors.isEmpty(), errors)
        }
        val expected = expectedKeys(booking)
        val relevant = active.filter { it.bookingRemoteId == booking.remoteId }
        val grouped = relevant.groupBy { it.roomRemoteId to it.businessDateMillis }

        val duplicateKeys = grouped.filterValues { it.size != 1 }.keys
        if (duplicateKeys.isNotEmpty()) errors += "Duplicate room-night financial lines"

        val actual = grouped.keys
        if ((expected - actual).isNotEmpty()) errors += "Missing room-night financial lines"
        if ((actual - expected).isNotEmpty()) errors += "Unexpected room-night financial lines"

        relevant.forEach { line ->
            val componentTotal = line.taxableAmount + line.gstAmount
            if (componentTotal > 0.0 && abs(componentTotal - line.grossAmount) > MONEY_TOLERANCE) {
                // OTA imports may deliberately store net payout in grossAmount. Their taxable
                // value plus tax remains the guest-facing room charge and is therefore valid.
                if (line.source != "OTA_IMPORT") errors += "Room-night tax components do not equal gross"
            }
            if (line.grossAmount < 0.0 || line.taxableAmount < 0.0 || line.gstAmount < 0.0) {
                errors += "Room-night financial amounts cannot be negative"
            }
        }

        return RoomNightFinancialIntegrityResult(errors.isEmpty(), errors.distinct())
    }

    fun expectedKeys(booking: BookingEntity): Set<Pair<String, Long>> {
        if (booking.checkOutMillis <= booking.checkInMillis) return emptySet()
        val dates = generateSequence(booking.checkInMillis) { it + DAY_MILLIS }
            .takeWhile { it < booking.checkOutMillis }
            .toList()
        return booking.roomRemoteIds.distinct().flatMap { roomId ->
            dates.map { date -> roomId to date }
        }.toSet()
    }
}

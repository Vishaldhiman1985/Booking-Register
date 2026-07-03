package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity

/**
 * Prepares a booking from the same financial-line state that will be persisted.
 *
 * The first pass validates the form and reserves stable booking identifiers. The
 * financial lines are then refreshed with those identifiers, and the second pass
 * rebuilds all booking aggregates from the refreshed lines.
 */
object BookingSavePreparation {
    fun prepare(
        buildBooking: (forcedRemoteId: String?, forcedBookingUuid: String?) -> BookingEntity?,
        refreshFinancialLines: (BookingEntity) -> Unit
    ): BookingEntity? {
        val preliminaryBooking = buildBooking(null, null) ?: return null

        refreshFinancialLines(preliminaryBooking)

        return buildBooking(
            preliminaryBooking.remoteId,
            preliminaryBooking.bookingUuid
        )
    }
}

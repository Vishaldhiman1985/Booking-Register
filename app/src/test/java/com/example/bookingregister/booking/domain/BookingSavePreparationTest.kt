package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class BookingSavePreparationTest {
    @Test
    fun rebuildsBookingAfterFinancialLinesRefreshAndKeepsStableIdentity() {
        var financialLineGross = 12_000.0
        var buildCount = 0
        var preliminaryRemoteId = ""
        var preliminaryBookingUuid = ""

        val prepared = BookingSavePreparation.prepare(
            buildBooking = { forcedRemoteId, forcedBookingUuid ->
                buildCount += 1
                booking(
                    remoteId = forcedRemoteId ?: UUID.randomUUID().toString(),
                    bookingUuid = forcedBookingUuid ?: UUID.randomUUID().toString(),
                    grossCharges = financialLineGross
                ).also {
                    if (buildCount == 1) {
                        preliminaryRemoteId = it.remoteId
                        preliminaryBookingUuid = it.bookingUuid
                    }
                }
            },
            refreshFinancialLines = {
                assertEquals(12_000.0, it.grossCharges, 0.01)
                financialLineGross = 25_000.0
            }
        )

        requireNotNull(prepared)
        assertEquals(2, buildCount)
        assertEquals(25_000.0, prepared.grossCharges, 0.01)
        assertNotEquals("", preliminaryRemoteId)
        assertNotEquals("", preliminaryBookingUuid)
        assertEquals(preliminaryRemoteId, prepared.remoteId)
        assertEquals(preliminaryBookingUuid, prepared.bookingUuid)
    }

    @Test
    fun doesNotRefreshLinesWhenInitialValidationFails() {
        var refreshCalled = false

        val prepared = BookingSavePreparation.prepare(
            buildBooking = { _, _ -> null },
            refreshFinancialLines = { refreshCalled = true }
        )

        assertNull(prepared)
        assertEquals(false, refreshCalled)
    }

    private fun booking(
        remoteId: String,
        bookingUuid: String,
        grossCharges: Double
    ): BookingEntity {
        return BookingEntity(
            remoteId = remoteId,
            bookingUuid = bookingUuid,
            hotelRemoteId = "hotel",
            guestName = "Guest",
            checkInMillis = 1_000L,
            checkOutMillis = 2_000L,
            roomRemoteIds = listOf("room"),
            grossCharges = grossCharges,
            rate = grossCharges,
            receivable = grossCharges
        )
    }
}

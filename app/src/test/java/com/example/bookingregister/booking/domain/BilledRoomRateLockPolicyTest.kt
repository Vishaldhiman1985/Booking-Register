package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilledRoomRateLockPolicyTest {
    @Test
    fun rejectsRoomPriceMutationAfterBilling() {
        val persisted = booking(gross = 15_000.0)

        assertTrue(
            BilledRoomRateLockPolicy.bookingFinancialsChanged(
                persisted,
                persisted.copy(rate = 20_000.0, receivable = 20_000.0, grossCharges = 20_000.0)
            )
        )
    }

    @Test
    fun permitsGuestMetadataMutationAfterBilling() {
        val persisted = booking(gross = 15_000.0)

        assertFalse(
            BilledRoomRateLockPolicy.bookingFinancialsChanged(
                persisted,
                persisted.copy(guestName = "Corrected guest name", notes = "ID verified")
            )
        )
    }

    @Test
    fun ignoresSyncMetadataButRejectsRoomLineMutation() {
        val persisted = line(gross = 15_000.0)

        assertFalse(
            BilledRoomRateLockPolicy.financialLinesChanged(
                listOf(persisted),
                listOf(persisted.copy(syncState = "PENDING", updatedAt = 99L, revision = 4L))
            )
        )
        assertTrue(
            BilledRoomRateLockPolicy.financialLinesChanged(
                listOf(persisted),
                listOf(persisted.copy(grossAmount = 20_000.0, taxableAmount = 19_047.62))
            )
        )
    }

    private fun booking(gross: Double) = BookingEntity(
        remoteId = "booking",
        bookingUuid = "booking-uuid",
        hotelRemoteId = "hotel",
        guestName = "Guest",
        checkInMillis = 1_000L,
        checkOutMillis = 2_000L,
        roomRemoteIds = listOf("room"),
        rate = gross,
        receivable = gross,
        grossCharges = gross,
        roomRevenue = gross
    )

    private fun line(gross: Double) = BookingFinancialLineEntity(
        remoteId = "line",
        hotelRemoteId = "hotel",
        bookingRemoteId = "booking",
        roomRemoteId = "room",
        businessDateMillis = 1_000L,
        grossAmount = gross,
        taxableAmount = gross
    )
}

package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.FolioSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class FinalBillGenerationPolicyTest {
    @Test
    fun otaFinalBillDoesNotCollectRoomReceivableFromGuest() {
        val pending = FinalBillGenerationPolicy.pendingGuestPayableBalance(
            booking = booking(sourceType = BookingSourceType.OTA),
            summary = summary(stayTotal = 12_400.0, stayPaid = 0.0)
        )

        assertEquals(0.0, pending, 0.001)
    }

    @Test
    fun otaFinalBillCollectsOnlyFoodAndServiceBalanceFromGuest() {
        val pending = FinalBillGenerationPolicy.pendingGuestPayableBalance(
            booking = booking(sourceType = BookingSourceType.OTA),
            summary = summary(
                stayTotal = 12_400.0,
                foodTotal = 160.0,
                serviceTotal = 50.0,
                stayPaid = 0.0
            )
        )

        assertEquals(210.0, pending, 0.001)
    }

    @Test
    fun directFinalBillRequiresFullFolioBalanceFromGuest() {
        val pending = FinalBillGenerationPolicy.pendingGuestPayableBalance(
            booking = booking(sourceType = BookingSourceType.DIRECT),
            summary = summary(stayTotal = 5_000.0, foodTotal = 160.0, stayPaid = 3_000.0)
        )

        assertEquals(2_160.0, pending, 0.001)
    }

    private fun booking(sourceType: String): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        sourceType = sourceType,
        checkInMillis = 0L,
        checkOutMillis = 1L,
        roomRemoteIds = listOf("room_1")
    )

    private fun summary(
        stayTotal: Double,
        foodTotal: Double = 0.0,
        serviceTotal: Double = 0.0,
        damageTotal: Double = 0.0,
        stayPaid: Double = 0.0,
        foodPaid: Double = 0.0,
        servicePaid: Double = 0.0,
        damagePaid: Double = 0.0
    ): FolioSummary = FolioSummary(
        stayTotal = stayTotal,
        foodTotal = foodTotal,
        serviceTotal = serviceTotal,
        damageTotal = damageTotal,
        stayPaid = stayPaid,
        foodPaid = foodPaid,
        servicePaid = servicePaid,
        damagePaid = damagePaid,
        unappliedPaid = 0.0
    )
}

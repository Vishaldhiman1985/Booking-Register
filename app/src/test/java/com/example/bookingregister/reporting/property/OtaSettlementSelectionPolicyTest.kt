package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class OtaSettlementSelectionPolicyTest {
    @Test
    fun `selected booking total is derived only from the bookings user ticked`() {
        val selected = listOf(
            row("a", 3500.0),
            row("b", 8500.0)
        )

        val summary = OtaSettlementSelectionPolicy.summarize(selected)

        assertEquals(2, summary.bookingCount)
        assertEquals(12000.0, summary.totalAmount, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `same booking cannot be selected twice`() {
        OtaSettlementSelectionPolicy.summarize(
            listOf(row("same", 3000.0), row("same", 3000.0))
        )
    }

    private fun row(id: String, outstanding: Double) =
        PropertyBalanceBookingFacts(
            bookingRemoteId = id,
            guestName = "Guest $id",
            sourceType = BookingSourceType.OTA,
            sourceRemoteId = "agoda-a",
            sourceName = "Agoda",
            receivable = outstanding,
            received = 0.0,
            outstanding = outstanding,
            storedPaidCache = 0.0,
            storedBalanceCache = outstanding
        )
}
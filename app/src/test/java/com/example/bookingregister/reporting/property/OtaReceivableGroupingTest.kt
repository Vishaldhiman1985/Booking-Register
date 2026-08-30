package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtaReceivableGroupingTest {
    @Test
    fun `groups OTA receivables company wise and excludes direct balances`() {
        val rows = listOf(
            row("a", "A Guest", BookingSourceType.OTA, "agoda-a", "Agoda", 4000.0),
            row("b", "B Guest", BookingSourceType.OTA, "agoda-a", "agoda", 8000.0),
            row("c", "C Guest", BookingSourceType.DIRECT, null, "Direct", 5000.0)
        )

        val groups = OtaReceivableGrouping.build(rows)

        assertEquals(1, groups.size)
        assertEquals("Agoda", groups.single().sourceName)
        assertEquals("agoda-a", groups.single().sourceRemoteId)
        assertEquals(2, groups.single().bookingCount)
        assertEquals(12000.0, groups.single().totalOutstanding, 0.001)
    }

    @Test
    fun `consolidated same OTA name with different property source ids cannot be settled as one source`() {
        val rows = listOf(
            row("a", "A", BookingSourceType.OTA, "agoda-property-a", "Agoda", 4000.0),
            row("b", "B", BookingSourceType.OTA, "agoda-property-b", "Agoda", 5000.0)
        )

        val group = OtaReceivableGrouping.build(rows).single()

        assertEquals(9000.0, group.totalOutstanding, 0.001)
        assertNull(group.sourceRemoteId)
    }

    private fun row(
        id: String,
        guest: String,
        sourceType: String,
        sourceRemoteId: String?,
        sourceName: String?,
        outstanding: Double
    ) = PropertyBalanceBookingFacts(
        bookingRemoteId = id,
        guestName = guest,
        sourceType = sourceType,
        sourceRemoteId = sourceRemoteId,
        sourceName = sourceName,
        receivable = outstanding,
        received = 0.0,
        outstanding = outstanding,
        storedPaidCache = 0.0,
        storedBalanceCache = outstanding
    )
}
package com.example.bookingregister.reporting.property

import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyRevenuePeriodSummaryBuilderTest {

    private val builder = PropertyRevenuePeriodSummaryBuilder()

    @Test
    fun build_attributesStoredSettlementValuesByRevenueShareWithoutRecalculatingRules() {
        val facts = PropertyRevenueFacts(
            grossRoomBilling = 3_360.0,
            roomRevenue = 3_000.0,
            gstCollected = 360.0,
            cgstCollected = 180.0,
            sgstCollected = 180.0,
            cessCollected = 0.0,
            paymentsRecordedInPeriod = 2_500.0,
            refundsRecordedInPeriod = 100.0,
            bookingSettlements = listOf(
                BookingSettlementFacts(
                    bookingRemoteId = "booking_1",
                    sourceType = "OTA",
                    roomRevenueInReportPeriod = 1_000.0,
                    fullBookingRoomRevenueFromFinancialLines = 4_000.0,
                    storedCommissionAmount = 400.0,
                    storedCommissionTax = 72.0,
                    storedSourceFee = 40.0,
                    storedTdsAmount = 80.0,
                    storedTcsAmount = 40.0,
                    storedExpectedPayout = 3_200.0
                ),
                BookingSettlementFacts(
                    bookingRemoteId = "booking_2",
                    sourceType = "OTA",
                    roomRevenueInReportPeriod = 2_000.0,
                    fullBookingRoomRevenueFromFinancialLines = 2_000.0,
                    storedCommissionAmount = 200.0,
                    storedCommissionTax = 36.0,
                    storedSourceFee = 20.0,
                    storedTdsAmount = 40.0,
                    storedTcsAmount = 20.0,
                    storedExpectedPayout = 1_600.0
                )
            )
        )

        val result = builder.build(facts)

        assertEquals(3_360.0, result.grossRoomBilling, 0.001)
        assertEquals(3_000.0, result.roomRevenueExGst, 0.001)
        assertEquals(360.0, result.gstCollected, 0.001)
        assertEquals(180.0, result.cgstCollected, 0.001)
        assertEquals(180.0, result.sgstCollected, 0.001)

        assertEquals(300.0, result.commissionAttributed, 0.001)
        assertEquals(54.0, result.commissionGstAttributed, 0.001)
        assertEquals(30.0, result.sourceFeesAttributed, 0.001)
        assertEquals(60.0, result.tdsAttributed, 0.001)
        assertEquals(30.0, result.tcsAttributed, 0.001)
        assertEquals(2_400.0, result.expectedPayoutAttributed, 0.001)

        assertEquals(2_500.0, result.paymentsRecordedInPeriod, 0.001)
        assertEquals(100.0, result.refundsRecordedInPeriod, 0.001)
    }

    @Test
    fun build_doesNotAttributeSettlementWhenStoredFullBookingRevenueIsZero() {
        val facts = PropertyRevenueFacts(
            grossRoomBilling = 0.0,
            roomRevenue = 0.0,
            gstCollected = 0.0,
            cgstCollected = 0.0,
            sgstCollected = 0.0,
            cessCollected = 0.0,
            paymentsRecordedInPeriod = 0.0,
            refundsRecordedInPeriod = 0.0,
            bookingSettlements = listOf(
                BookingSettlementFacts(
                    bookingRemoteId = "booking_zero",
                    sourceType = "OTA",
                    roomRevenueInReportPeriod = 1_000.0,
                    fullBookingRoomRevenueFromFinancialLines = 0.0,
                    storedCommissionAmount = 500.0,
                    storedCommissionTax = 90.0,
                    storedSourceFee = 50.0,
                    storedTdsAmount = 100.0,
                    storedTcsAmount = 50.0,
                    storedExpectedPayout = 4_000.0
                )
            )
        )

        val result = builder.build(facts)

        assertEquals(0.0, result.commissionAttributed, 0.001)
        assertEquals(0.0, result.commissionGstAttributed, 0.001)
        assertEquals(0.0, result.sourceFeesAttributed, 0.001)
        assertEquals(0.0, result.tdsAttributed, 0.001)
        assertEquals(0.0, result.tcsAttributed, 0.001)
        assertEquals(0.0, result.expectedPayoutAttributed, 0.001)
    }
}

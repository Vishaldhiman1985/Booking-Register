package com.example.bookingregister.reporting.property

/**
 * Exact accounting facts read from existing stored records.
 * No GST, OTA commission, TDS, TCS or payout formula is recalculated here.
 */
data class PropertyRevenueFacts(
    val grossRoomBilling: Double,
    val roomRevenue: Double,
    val gstCollected: Double,
    val cgstCollected: Double,
    val sgstCollected: Double,
    val cessCollected: Double,
    val paymentsRecordedInPeriod: Double,
    val refundsRecordedInPeriod: Double,
    val bookingSettlements: List<BookingSettlementFacts>
)

data class BookingSettlementFacts(
    val bookingRemoteId: String,
    val sourceType: String,
    val roomRevenueInReportPeriod: Double,
    val fullBookingRoomRevenueFromFinancialLines: Double,
    val storedCommissionAmount: Double,
    val storedCommissionTax: Double,
    val storedSourceFee: Double,
    val storedTdsAmount: Double,
    val storedTcsAmount: Double,
    val storedExpectedPayout: Double
)

package com.example.bookingregister.reporting.property

/**
 * Reporting-only revenue summary for one property/date scope.
 *
 * All GST / commission / TDS / TCS values originate from already-stored
 * accounting facts. This class never saves data and never calls sync.
 *
 * Full-booking OTA/source deductions are attributed to the selected reporting
 * period according to that period's share of the booking's stored room revenue.
 * No GST, commission, TDS or TCS percentage/formula is recalculated here.
 */
data class PropertyRevenuePeriodSummary(
    val grossRoomBilling: Double,
    val roomRevenueExGst: Double,
    val gstCollected: Double,
    val cgstCollected: Double,
    val sgstCollected: Double,
    val cessCollected: Double,

    val commissionAttributed: Double,
    val commissionGstAttributed: Double,
    val sourceFeesAttributed: Double,
    val tdsAttributed: Double,
    val tcsAttributed: Double,
    val expectedPayoutAttributed: Double,

    val paymentsRecordedInPeriod: Double,
    val refundsRecordedInPeriod: Double
)

class PropertyRevenuePeriodSummaryBuilder {

    fun build(facts: PropertyRevenueFacts): PropertyRevenuePeriodSummary {
        var commission = 0.0
        var commissionGst = 0.0
        var sourceFees = 0.0
        var tds = 0.0
        var tcs = 0.0
        var expectedPayout = 0.0

        facts.bookingSettlements.forEach { settlement ->
            val fullBookingRevenue =
                settlement.fullBookingRoomRevenueFromFinancialLines.coerceAtLeast(0.0)

            val periodShare =
                if (fullBookingRevenue <= MONEY_EPSILON) {
                    0.0
                } else {
                    (
                        settlement.roomRevenueInReportPeriod.coerceAtLeast(0.0) /
                            fullBookingRevenue
                        ).coerceIn(0.0, 1.0)
                }

            commission += settlement.storedCommissionAmount.coerceAtLeast(0.0) * periodShare
            commissionGst += settlement.storedCommissionTax.coerceAtLeast(0.0) * periodShare
            sourceFees += settlement.storedSourceFee.coerceAtLeast(0.0) * periodShare
            tds += settlement.storedTdsAmount.coerceAtLeast(0.0) * periodShare
            tcs += settlement.storedTcsAmount.coerceAtLeast(0.0) * periodShare
            expectedPayout += settlement.storedExpectedPayout.coerceAtLeast(0.0) * periodShare
        }

        return PropertyRevenuePeriodSummary(
            grossRoomBilling = facts.grossRoomBilling,
            roomRevenueExGst = facts.roomRevenue,
            gstCollected = facts.gstCollected,
            cgstCollected = facts.cgstCollected,
            sgstCollected = facts.sgstCollected,
            cessCollected = facts.cessCollected,

            commissionAttributed = commission,
            commissionGstAttributed = commissionGst,
            sourceFeesAttributed = sourceFees,
            tdsAttributed = tds,
            tcsAttributed = tcs,
            expectedPayoutAttributed = expectedPayout,

            paymentsRecordedInPeriod = facts.paymentsRecordedInPeriod,
            refundsRecordedInPeriod = facts.refundsRecordedInPeriod
        )
    }

    private companion object {
        const val MONEY_EPSILON = 0.001
    }
}

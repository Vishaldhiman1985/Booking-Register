package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingSourceType
import java.util.Locale

/**
 * Read-only Balance use-case.
 *
 * Single-source-of-truth rule:
 * - raw records enter through PropertyReportingEngine
 * - PropertyReportingEngine produces the authoritative Balance facts
 * - this class only organizes those facts for display
 *
 * No financial formula is duplicated here.
 * No save/update/delete/sync operation exists here.
 */
class PropertyBalanceReportEngine(
    private val reportingEngine: PropertyReportingEngine =
        PropertyReportingEngine()
) {

    fun build(
        raw: PropertyReportRawData,
        scope: PropertyReportScope
    ): PropertyBalanceReport {

        val reportingSnapshot =
            reportingEngine.build(raw, scope)

        val facts = reportingSnapshot.balance

        val otaReceivables =
            OtaReceivableGrouping.build(facts.bookings)

        val directGuestBalances =
            facts.bookings
                .asSequence()
                .filter {
                    it.sourceType != BookingSourceType.OTA &&
                        it.outstanding > 0.001
                }
                .sortedWith(
                    compareByDescending<PropertyBalanceBookingFacts> {
                        it.outstanding
                    }.thenBy {
                        it.guestName.lowercase(Locale.ROOT)
                    }.thenBy {
                        it.bookingRemoteId
                    }
                )
                .toList()

        return PropertyBalanceReport(
            scope = scope,
            facts = facts,
            otaReceivables = otaReceivables,
            directGuestBalances = directGuestBalances
        )
    }
}

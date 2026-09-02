package com.example.bookingregister.reporting.property

/**
 * Immutable read-only Balance report presented to the UI.
 *
 * All monetary facts originate from PropertyBalanceFacts, which is produced
 * by the authoritative property reporting pipeline.
 *
 * This model does not calculate, save, update, sync or mutate business data.
 */
data class PropertyBalanceReport(
    val scope: PropertyReportScope,
    val facts: PropertyBalanceFacts,
    val otaReceivables: List<OtaReceivableGroup>,
    val directGuestBalances: List<PropertyBalanceBookingFacts>
)

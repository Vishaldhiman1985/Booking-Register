package com.example.bookingregister.reporting.property

import com.example.bookingregister.common.domain.DateRange

data class PropertyReportingSnapshot(
    val dataset: PropertyReportDataset,
    val revenue: PropertyRevenueFacts,
    val occupancy: PropertyOccupancyFacts,
    val balance: PropertyBalanceFacts
)

/**
 * Single read-only entry point for property reporting.
 *
 * It scopes the already-read Room data first, then passes only that property's
 * records to Revenue, Occupancy and Balance builders.
 */
class PropertyReportingEngine(
    private val reportBuilder: PropertyReportBuilder = PropertyReportBuilder(),
    private val occupancyBuilder: PropertyOccupancyReportBuilder = PropertyOccupancyReportBuilder(),
    private val balanceBuilder: PropertyBalanceReportBuilder = PropertyBalanceReportBuilder()
) {
    fun build(raw: PropertyReportRawData, scope: PropertyReportScope): PropertyReportingSnapshot {
        val dataset = reportBuilder.scope(raw, scope)
        val range = DateRange(scope.startMillis, scope.endMillis)

        return PropertyReportingSnapshot(
            dataset = dataset,
            revenue = reportBuilder.revenueFacts(dataset),
            occupancy = occupancyBuilder.build(dataset, range),
            balance = balanceBuilder.build(dataset)
        )
    }
}

package com.example.bookingregister.reporting.property

/**
 * Read-only property revenue report entry point.
 *
 * It scopes already-read local data and converts the stored accounting facts
 * into a period summary. It performs no database writes and does not call sync.
 */
class PropertyRevenueReportEngine(
    private val reportBuilder: PropertyReportBuilder = PropertyReportBuilder(),
    private val summaryBuilder: PropertyRevenuePeriodSummaryBuilder =
        PropertyRevenuePeriodSummaryBuilder()
) {

    fun build(
        raw: PropertyReportRawData,
        scope: PropertyReportScope
    ): PropertyRevenuePeriodSummary {
        val dataset = reportBuilder.scope(raw, scope)
        val facts = reportBuilder.revenueFacts(dataset)
        return summaryBuilder.build(facts)
    }
}

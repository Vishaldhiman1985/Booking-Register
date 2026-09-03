package com.example.bookingregister.reporting.property

/**
 * Read-only property occupancy report engine.
 *
 * One property-scoped dataset is built from an already-read Room snapshot, then
 * the existing PropertyOccupancyReportBuilder / OccupancyCalculator rules are
 * reused for every headline and chart bucket. No occupancy rule is duplicated
 * here and this engine performs no writes or synchronization.
 */
class PropertyOccupancyReportEngine(
    private val reportBuilder: PropertyReportBuilder = PropertyReportBuilder(),
    private val occupancyBuilder: PropertyOccupancyReportBuilder = PropertyOccupancyReportBuilder()
) {
    fun build(
        raw: PropertyReportRawData,
        scope: PropertyReportScope,
        annual: PropertyOccupancyPeriodRequest,
        monthly: PropertyOccupancyPeriodRequest,
        weekly: PropertyOccupancyPeriodRequest
    ): PropertyOccupancyReport {
        val dataset = reportBuilder.scope(raw, scope)

        return PropertyOccupancyReport(
            annual = buildPeriod(dataset, annual),
            monthly = buildPeriod(dataset, monthly),
            weekly = buildPeriod(dataset, weekly)
        )
    }

    private fun buildPeriod(
        dataset: PropertyReportDataset,
        request: PropertyOccupancyPeriodRequest
    ): PropertyOccupancyPeriodReport {
        val facts = occupancyBuilder.build(
            dataset = dataset,
            range = request.range
        )

        val entries = request.buckets.map { bucket ->
            bucket.label to occupancyBuilder.chartBucketPercent(
                dataset = dataset,
                range = bucket.range
            ).toDouble()
        }

        return PropertyOccupancyPeriodReport(
            facts = facts,
            entries = entries
        )
    }
}
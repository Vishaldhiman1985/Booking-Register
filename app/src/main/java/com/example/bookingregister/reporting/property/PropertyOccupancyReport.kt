package com.example.bookingregister.reporting.property

import com.example.bookingregister.common.domain.DateRange

/**
 * Immutable, display-ready occupancy reporting models.
 *
 * These models contain reporting facts only. They have no repository, DAO,
 * Firebase, payment, booking-save or sync behavior.
 */
data class PropertyOccupancyBucket(
    val label: String,
    val range: DateRange
)

data class PropertyOccupancyPeriodRequest(
    val range: DateRange,
    val buckets: List<PropertyOccupancyBucket>
)

data class PropertyOccupancyPeriodReport(
    val facts: PropertyOccupancyFacts,
    val entries: List<Pair<String, Double>>
)

data class PropertyOccupancyReport(
    val annual: PropertyOccupancyPeriodReport,
    val monthly: PropertyOccupancyPeriodReport,
    val weekly: PropertyOccupancyPeriodReport
)
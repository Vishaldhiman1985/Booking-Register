package com.example.bookingregister.revenue.domain

data class CategoryPerformanceSummary(
    val categoryName: String,
    val categoryColor: String,
    val occupiedRoomNights: Int,
    val availableRoomNights: Int,
    val revenue: Double
) {
    val occupancyPercent: Int
        get() = if (availableRoomNights <= 0) {
            0
        } else {
            ((occupiedRoomNights.toDouble() / availableRoomNights) * 100).toInt()
        }
}
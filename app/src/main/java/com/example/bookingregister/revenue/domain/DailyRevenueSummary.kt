package com.example.bookingregister.revenue.domain

data class DailyRevenueSummary(
    val businessDateMillis: Long,
    val roomRevenue: Double,
    val paymentsReceived: Double,
    val pendingBalance: Double
)

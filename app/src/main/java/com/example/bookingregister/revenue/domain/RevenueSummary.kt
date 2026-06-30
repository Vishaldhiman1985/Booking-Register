package com.example.bookingregister.revenue.domain

data class RevenueSummary(
    val roomRevenue: Double,
    val paymentsReceived: Double,
    val discounts: Double,
    val refunds: Double,
    val adjustments: Double,
    val pendingBalance: Double
)

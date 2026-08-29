package com.example.bookingregister.reporting.property

data class PropertyBalanceFacts(
    val totalReceivable: Double,
    val totalReceived: Double,
    val totalOutstanding: Double,
    val otaOutstanding: Double,
    val guestOutstanding: Double,
    val openBookingCount: Int,
    val bookings: List<PropertyBalanceBookingFacts>
)

data class PropertyBalanceBookingFacts(
    val bookingRemoteId: String,
    val guestName: String,
    val sourceType: String,
    val sourceRemoteId: String?,
    val sourceName: String?,
    val receivable: Double,
    val received: Double,
    val outstanding: Double,
    /**
     * Existing BookingEntity cache values are kept only for audit/comparison.
     * The report totals above are built from the stored receivable policy plus
     * actual payment/refund/correction rows.
     */
    val storedPaidCache: Double,
    val storedBalanceCache: Double
)

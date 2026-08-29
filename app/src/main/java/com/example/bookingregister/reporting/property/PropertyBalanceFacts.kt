package com.example.bookingregister.reporting.property

data class PropertyBalanceFacts(
    val totalReceivable: Double,
    val totalReceived: Double,
    val totalOutstanding: Double,
    val otaOutstanding: Double,
    val guestOutstanding: Double,
    val openBookingCount: Int,
    val bookings: List<PropertyBalanceBookingFacts>,
    /**
     * Portion of actual received money that is applied against each booking's
     * own reporting receivable. Excess on one booking is never used to reduce
     * another booking's outstanding balance.
     */
    val totalAppliedReceived: Double =
        (totalReceivable - totalOutstanding).coerceAtLeast(0.0),
    /**
     * Actual received money above the booking-level reporting receivable.
     * This is reported separately; it does not automatically settle another booking.
     */
    val totalExcessPayment: Double =
        (totalReceived - totalAppliedReceived).coerceAtLeast(0.0)
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
    val storedBalanceCache: Double,
    /**
     * Received money actually applied to this booking's receivable.
     */
    val appliedReceived: Double =
        (receivable - outstanding).coerceAtLeast(0.0),
    /**
     * Received money above this booking's reporting receivable.
     */
    val excessPayment: Double =
        (received - appliedReceived).coerceAtLeast(0.0)
)

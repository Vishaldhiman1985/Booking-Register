package com.example.bookingregister.reporting.property

data class OtaSettlementSelectionSummary(
    val bookingCount: Int,
    val totalAmount: Double
)

object OtaSettlementSelectionPolicy {
    fun summarize(rows: List<PropertyBalanceBookingFacts>): OtaSettlementSelectionSummary {
        require(rows.isNotEmpty()) { "Select at least one OTA booking." }
        require(rows.map { it.bookingRemoteId }.distinct().size == rows.size) {
            "The same OTA booking cannot be selected twice."
        }
        require(rows.all { it.outstanding > 0.001 }) {
            "Only bookings with a positive pending OTA receivable can be settled."
        }
        return OtaSettlementSelectionSummary(
            bookingCount = rows.size,
            totalAmount = rows.sumOf { it.outstanding }
        )
    }
}
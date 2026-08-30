package com.example.bookingregister.data.repository

/**
 * One user-selected OTA booking and the outstanding amount visible when the
 * confirmation screen was opened. The server re-calculates this amount from
 * current cloud payment rows before committing anything.
 */
data class OtaSettlementSelection(
    val bookingRemoteId: String,
    val expectedOutstanding: Double
)
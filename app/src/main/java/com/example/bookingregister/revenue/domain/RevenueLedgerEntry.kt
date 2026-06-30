package com.example.bookingregister.revenue.domain

data class RevenueLedgerEntry(
    val bookingRemoteId: String,
    val businessDateMillis: Long,
    val type: String,
    val amount: Double,
    val guestName: String,
    val roomRemoteId: String? = null,
    val source: String = RevenueLedgerSource.MINI_FOLIO
)

object RevenueLedgerEntryType {
    const val ROOM_REVENUE = "ROOM_REVENUE"
    const val PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
    const val DISCOUNT = "DISCOUNT"
    const val REFUND = "REFUND"
    const val ADJUSTMENT = "ADJUSTMENT"
}

object RevenueLedgerSource {
    const val MINI_FOLIO = "MINI_FOLIO"
}

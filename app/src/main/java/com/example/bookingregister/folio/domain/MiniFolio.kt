package com.example.bookingregister.folio.domain

data class MiniFolio(
    val bookingRemoteId: String,
    val hotelRemoteId: String,
    val guestName: String,
    val status: String,
    val lines: List<MiniFolioLine>,
    val integrityErrors: List<String> = emptyList()
) {
    val totalCharges: Double
        get() = lines.filter { it.kind == MiniFolioLineKind.CHARGE }.sumOf { it.amount }

    val totalPayments: Double
        get() = lines.filter { it.kind == MiniFolioLineKind.PAYMENT }.sumOf { it.amount }

    val totalDiscounts: Double
        get() = lines.filter { it.kind == MiniFolioLineKind.DISCOUNT }.sumOf { it.amount }

    val totalRefunds: Double
        get() = lines.filter { it.kind == MiniFolioLineKind.REFUND }.sumOf { it.amount }

    val balance: Double
        get() = (totalCharges - totalDiscounts - totalPayments + totalRefunds).coerceAtLeast(0.0)
}

object MiniFolioStatus {
    const val OPEN = "OPEN"
    const val SETTLED = "SETTLED"
    const val CANCELLED = "CANCELLED"
    const val INTEGRITY_ERROR = "INTEGRITY_ERROR"
    const val PRICING_PENDING = "PRICING_PENDING"
}

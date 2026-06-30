package com.example.bookingregister.folio.domain

data class MiniFolioLine(
    val bookingRemoteId: String,
    val businessDateMillis: Long,
    val type: String,
    val kind: String,
    val amount: Double,
    val description: String,
    val roomRemoteId: String? = null,
    val accountBucket: String? = null
)

object MiniFolioLineType {
    const val ROOM_CHARGE = "ROOM_CHARGE"
    const val FOOD_CHARGE = "FOOD_CHARGE"
    const val SERVICE_CHARGE = "SERVICE_CHARGE"
    const val DAMAGE_CHARGE = "DAMAGE_CHARGE"
    const val PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
    const val DISCOUNT = "DISCOUNT"
    const val REFUND = "REFUND"
    const val ADJUSTMENT = "ADJUSTMENT"
}

object MiniFolioLineKind {
    const val CHARGE = "CHARGE"
    const val PAYMENT = "PAYMENT"
    const val DISCOUNT = "DISCOUNT"
    const val REFUND = "REFUND"
    const val ADJUSTMENT = "ADJUSTMENT"
}

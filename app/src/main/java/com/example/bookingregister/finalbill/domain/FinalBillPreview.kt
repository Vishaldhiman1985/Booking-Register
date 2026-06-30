package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.MiniFolioLine

data class FinalBillPreview(
    val bookingRemoteId: String,
    val guestName: String,
    val roomNames: String,
    val sourceName: String,
    val sourceType: String = "DIRECT",

    val roomCharges: Double,
    val roomPaid: Double,
    val roomBalance: Double,
    val roomItems: List<FinalBillRoomItemPreview> = emptyList(),


    val foodOrders: List<FinalBillFoodOrderPreview>,
    val foodItems: List<FinalBillFoodItemPreview>,
    val gstSummaries: List<FinalBillGstSummary>,

    val foodSubtotal: Double,
    val foodGst: Double,
    val foodTotal: Double,
    val foodPaid: Double = 0.0,
    val foodBalance: Double = 0.0,

    val extraTotal: Double = 0.0,
    val servicePaid: Double = 0.0,
    val serviceBalance: Double = 0.0,
    val damagePaid: Double = 0.0,
    val damageBalance: Double = 0.0,
    val roomDiscount: Double = 0.0,
    val foodDiscount: Double = 0.0,
    val serviceDiscount: Double = 0.0,
    val discounts: Double = 0.0,

    val totalCharges: Double,
    val totalPaid: Double,
    val folioBalance: Double,

    val folioLines: List<MiniFolioLine> = emptyList(),
    val checkoutBalanceOverride: Double? = null
) {
    val finalBillTotal: Double
        get() = totalCharges

    val amountDue: Double
        get() = folioBalance

    val guestCheckoutBalance: Double
        get() = checkoutBalanceOverride ?: if (sourceType == BookingSourceType.OTA) {
            (foodBalance + extraTotal).coerceAtLeast(0.0)
        } else {
            folioBalance
        }
}

data class FinalBillFoodOrderPreview(
    val orderRemoteId: String,
    val orderNumber: String,
    val roomName: String,
    val orderMillis: Long,
    val totalAmount: Double,
    val itemCount: Int
)

data class FinalBillFoodItemPreview(
    val orderRemoteId: String,
    val roomName: String,
    val itemName: String,
    val quantity: Double,
    val unitPrice: Double,
    val taxableAmount: Double,
    val gstRatePercent: Double,
    val gstAmount: Double,
    val totalAmount: Double
)

data class FinalBillRoomItemPreview(
    val roomName: String,
    val businessDateMillis: Long,
    val grossAmount: Double,
    val taxableAmount: Double,
    val gstRatePercent: Double,
    val gstAmount: Double,
    val totalAmount: Double,
    val hsnSacCode: String? = null
)

data class FinalBillGstSummary(
    val gstRatePercent: Double,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val totalGstAmount: Double
)

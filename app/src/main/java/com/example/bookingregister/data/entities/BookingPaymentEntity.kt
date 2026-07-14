package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_payments",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["paymentCategory"]),
        Index(value = ["hotelRemoteId", "bookingRemoteId"])
    ]
)
data class BookingPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val bookingRemoteId: String,
    val originalPaymentRemoteId: String? = null,
    val paymentType: String = BookingPaymentType.PAYMENT,
    val paymentCategory: String = BookingPaymentCategory.AUTO,
    val amount: Double = 0.0,
    val allocatedStayAmount: Double = 0.0,
    val allocatedFoodAmount: Double = 0.0,
    val allocatedServiceAmount: Double = 0.0,
    val allocatedDamageAmount: Double = 0.0,
    val unappliedAmount: Double = 0.0,
    val paymentMillis: Long = System.currentTimeMillis(),
    val method: String? = null,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null

)

object BookingPaymentCategory {
    const val AUTO = "AUTO"
    const val STAY = "STAY"
    const val FOOD = "FOOD"
    const val SERVICE = "SERVICE"
    const val DAMAGE = "DAMAGE"

    val selectable = listOf(AUTO, STAY, FOOD, SERVICE, DAMAGE)

    fun normalize(value: String?): String {
        return when (value?.trim()?.uppercase()) {
            STAY -> STAY
            FOOD -> FOOD
            SERVICE -> SERVICE
            DAMAGE -> DAMAGE
            else -> AUTO
        }
    }
}

object BookingPaymentType {
    const val ADVANCE = "ADVANCE"
    const val PAYMENT = "PAYMENT"
    const val REFUND = "REFUND"
    const val ADJUSTMENT = "ADJUSTMENT"
}

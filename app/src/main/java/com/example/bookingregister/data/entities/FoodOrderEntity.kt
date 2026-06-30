package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_orders",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["billRemoteId"]),
        Index(value = ["foodBillingScope"]),
        Index(value = ["linkedFinalBillId"]),
        Index(value = ["roomRemoteId"]),
        Index(value = ["hotelRemoteId", "orderMillis"])
    ]
)
data class FoodOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,
    val bookingRemoteId: String? = null,
    val billRemoteId: String? = null,
    val orderNumber: String? = null,
    val foodBillingScope: String = FoodBillingScope.WALK_IN,
    val linkedFinalBillId: String? = null,
    val archivedAt: Long? = null,
    val roomRemoteId: String? = null,
    val roomName: String? = null,
    val guestName: String,
    val orderMillis: Long = System.currentTimeMillis(),
    val status: String = FoodOrderStatus.OPEN,
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxableAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

object FoodOrderStatus {
    const val OPEN = "OPEN"
    const val KOT = "KOT"
    const val FINALIZED = "FINALIZED"
    const val BILLED = "BILLED"
    const val BILLED_IN_FOLIO = "BILLED_IN_FOLIO"
    const val CANCELLED = "CANCELLED"
}

object FoodBillingScope {
    const val WALK_IN = "WALK_IN"
    const val IN_HOUSE_BOOKING = "IN_HOUSE_BOOKING"
}

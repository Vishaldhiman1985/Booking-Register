package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_bills",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["billNumber"], unique = true),
        Index(value = ["hotelRemoteId", "billMillis"])
    ]
)
data class FoodBillEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,
    val supplierName: String? = null,
    val supplierGstin: String? = null,
    val supplierAddress: String? = null,
    val supplierPhone: String? = null,
    val supplierState: String? = null,
    val propertyDisplayName: String? = null,
    val billNumber: String,
    val billMillis: Long = System.currentTimeMillis(),
    val guestName: String? = null,
    val guestMobile: String? = null,
    val guestAddress: String? = null,
    val guestGstin: String? = null,
    val roomsIncluded: String,
    val orderRemoteIds: String,
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxableAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val cessAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paymentMode: String? = null,
    val notes: String? = null,
    val status: String = FoodBillStatus.ISSUED,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

object FoodBillStatus {
    const val ISSUED = "ISSUED"
    const val CANCELLED = "CANCELLED"
}

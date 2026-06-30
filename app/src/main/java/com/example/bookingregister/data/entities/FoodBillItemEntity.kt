package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_bill_items",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["billRemoteId"]),
        Index(value = ["orderRemoteId"]),
        Index(value = ["roomName"])
    ]
)
data class FoodBillItemEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val billRemoteId: String,
    val orderRemoteId: String,
    val orderNumber: String? = null,
    val orderMillis: Long = 0,
    val roomName: String? = null,
    val menuItemRemoteId: String? = null,
    val itemName: String,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val lineSubtotal: Double = 0.0,
    val gstCategoryRemoteId: String? = null,
    val gstCategoryName: String? = null,
    val hsnSacCode: String? = null,
    val gstRatePercent: Double = 0.0,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,
    val taxableAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val cessAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val lineTotal: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

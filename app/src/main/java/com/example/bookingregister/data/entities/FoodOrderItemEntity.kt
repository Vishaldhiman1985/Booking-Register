package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_order_items",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["orderRemoteId"]),
        Index(value = ["menuItemRemoteId"])
    ]
)
data class FoodOrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val orderRemoteId: String,
    val menuItemRemoteId: String? = null,
    val itemName: String,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val gstRatePercent: Double = 5.0,
    val gstCategoryRemoteId: String? = null,
    val gstCategoryName: String? = null,
    val hsnSacCode: String? = null,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,
    val lineSubtotal: Double = 0.0,
    val lineGst: Double = 0.0,
    val lineTotal: Double = 0.0,
    val isCancelled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

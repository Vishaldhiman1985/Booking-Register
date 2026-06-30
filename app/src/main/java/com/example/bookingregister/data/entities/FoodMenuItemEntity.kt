package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_menu_items",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "itemName"])
    ]
)
data class FoodMenuItemEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,
    val itemName: String,
    val categoryName: String? = null,
    val price: Double = 0.0,
    val gstCategoryRemoteId: String? = null,
    val gstRatePercent: Double = 5.0,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null,
    val gstCategoryName: String? = null,
    val hsnSacCode: String? = null,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,
)

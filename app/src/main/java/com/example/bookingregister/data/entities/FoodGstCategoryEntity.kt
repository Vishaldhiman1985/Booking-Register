package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "food_gst_categories",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["hotelRemoteId", "categoryName"], unique = true)
    ]
)
data class FoodGstCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val categoryName: String,
    val hsnSacCode: String? = null,
    val gstRatePercent: Double = 0.0,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,
    val taxType: String = "GST",
    val itcType: String? = null,
    val description: String? = null,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

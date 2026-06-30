package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "service_menu_items",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "serviceName"])
    ]
)
data class ServiceMenuItemEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,
    val serviceName: String,
    val categoryName: String? = null,
    val description: String? = null,
    val unitLabel: String? = null,
    val price: Double = 0.0,
    val sacCode: String? = null,
    val gstRatePercent: Double = 18.0,
    val taxInclusive: Boolean = true,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.property.domain.RoomCategoryPalette

@Entity(
    tableName = "room_categories",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["hotelRemoteId", "categoryName"], unique = true),
        Index(value = ["sortOrder"])
    ]
)
data class RoomCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val categoryName: String = RoomCategoryPalette.DEFAULT_CATEGORY,
    val categoryColor: String = RoomCategoryPalette.DEFAULT_COLOR,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)
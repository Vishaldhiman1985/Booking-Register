package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rooms",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["categorySortOrder", "categoryName"])
    ]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    val remoteId: String,
    val hotelRemoteId: String,

    val roomName: String,
    val categoryName: String = "",
    val categoryColor: String = "#EEF0F2",
    val categorySortOrder: Int = 0,
    val propertyRemoteId: String? = null,
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

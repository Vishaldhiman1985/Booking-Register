package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_room_night_assignments",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["roomRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "businessDateMillis"]),
        Index(
            value = ["hotelRemoteId", "bookingRemoteId", "roomRemoteId", "businessDateMillis"],
            unique = true
        )
    ]
)
data class BookingRoomNightAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    val remoteId: String,
    val hotelRemoteId: String,
    val bookingRemoteId: String,
    val roomRemoteId: String,
    val propertyRemoteId: String? = null,
    val businessDateMillis: Long,

    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)
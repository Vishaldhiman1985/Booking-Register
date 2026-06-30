package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hotels")
data class HotelEntity(
    @PrimaryKey
    val remoteId: String,
    val hotelName: String,
    val gstNumber: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)


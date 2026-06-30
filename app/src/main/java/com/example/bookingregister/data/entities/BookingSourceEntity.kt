package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_sources",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "propertyRemoteId", "sourceName"], unique = true)
    ]
)
data class BookingSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,
    val sourceName: String,
    val sourceType: String = BookingSourceType.DIRECT,
    val commissionPercent: Double = 0.0,
    val commissionGstPercent: Double = 0.0,
    val tcsPercent: Double = 0.0,
    val tdsPercent: Double = 0.0,
    val fixedFee: Double = 0.0,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

object BookingSourceType {
    const val DIRECT = "DIRECT"
    const val AGENT = "AGENT"
    const val OTA = "OTA"
}

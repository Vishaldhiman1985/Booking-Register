package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "room_gst_slabs",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["effectiveFromMillis"])
    ]
)
data class RoomGstSlabEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,

    val slabName: String,
    val minGrossAmount: Double = 0.0,
    val maxGrossAmount: Double? = null,

    val gstRatePercent: Double = 0.0,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,

    val hsnSacCode: String = "996311",
    val notificationRef: String? = null,

    val effectiveFromMillis: Long = 0L,
    val effectiveToMillis: Long? = null,

    val isActive: Boolean = true,
    val isDeleted: Boolean = false,

    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: String = SyncState.SYNCED,
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)
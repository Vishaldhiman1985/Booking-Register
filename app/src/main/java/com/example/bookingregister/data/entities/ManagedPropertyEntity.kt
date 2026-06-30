package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.data.SyncState

@Entity(
    tableName = "managed_properties",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["hotelRemoteId", "propertyName"], unique = true),
        Index(value = ["hotelRemoteId", "sortOrder"])
    ]
)
data class ManagedPropertyEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val propertyName: String,
    val legalName: String? = null,
    val gstNumber: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val invoicePrefix: String? = null,
    val state: String? = null,
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

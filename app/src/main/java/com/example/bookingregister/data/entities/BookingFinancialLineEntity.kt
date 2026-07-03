package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_financial_lines",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "bookingRemoteId"]),
        Index(value = ["hotelRemoteId", "propertyRemoteId"]),
        Index(value = ["hotelRemoteId", "businessDateMillis"])
    ]
)
data class BookingFinancialLineEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val bookingRemoteId: String,
    val roomRemoteId: String,
    val propertyRemoteId: String? = null,
    val businessDateMillis: Long,
    val grossAmount: Double = 0.0,
    val taxableAmount: Double = 0.0,
    val gstRatePercent: Double = 0.0,
    val gstAmount: Double = 0.0,
    val hsnSacCode: String? = null,
    val slabRemoteId: String? = null,
    val slabName: String? = null,
    val cgstRatePercent: Double = 0.0,
    val sgstRatePercent: Double = 0.0,
    val cessRatePercent: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val cessAmount: Double = 0.0,
    val source: String = BookingFinancialLineSource.MANUAL,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

object BookingFinancialLineSource {
    const val SIMPLE_AUTO = "SIMPLE_AUTO"
    const val MANUAL = "MANUAL"
    const val OTA_IMPORT = "OTA_IMPORT"
    const val LEGACY_MIGRATION = "LEGACY_MIGRATION"
}

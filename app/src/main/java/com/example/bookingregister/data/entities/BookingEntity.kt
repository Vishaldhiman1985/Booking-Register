package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bookingregister.booking.domain.BookingStatus

@Entity(
    tableName = "bookings",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["bookingUuid"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["hotelRemoteId", "bookingStatus"]),
        Index(value = ["propertyRemoteId"])
    ]
)
data class BookingEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    val remoteId: String,
    val bookingUuid: String,
    val hotelRemoteId: String,
    val propertyRemoteId: String? = null,

    val guestName: String,
    val guestMobile: String? = null,
    val sourceName: String? = null,
    val sourceRemoteId: String? = null,
    val sourceType: String = BookingSourceType.DIRECT,
    val adultCount: Int = 1,
    val childCount: Int = 0,

    val checkInMillis: Long,
    val checkOutMillis: Long,

    val roomRemoteIds: List<String>,

    val rate: Double = 0.0,
    val receivable: Double = 0.0,
    val paid: Double = 0.0,
    val balance: Double = 0.0,
    val paymentStatus: String = "NOT_PAID",

    val grossCharges: Double = 0.0,
    val roomRevenue: Double = 0.0,
    val propertyTax: Double = 0.0,
    val commissionAmount: Double = 0.0,
    val commissionTax: Double = 0.0,
    val sourceFee: Double = 0.0,
    val tdsAmount: Double = 0.0,
    val tcsAmount: Double = 0.0,
    val expectedPayout: Double = 0.0,
    val bookingStatus: String = BookingStatus.RESERVED,
    val actualCheckInAt: Long? = null,
    val actualCheckOutAt: Long? = null,
    val checkoutNote: String? = null,
    val reopenNote: String? = null,
    val reopenedAt: Long? = null,
    val notes: String? = null,

    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)





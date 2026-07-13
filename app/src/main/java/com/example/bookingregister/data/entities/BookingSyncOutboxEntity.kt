package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_sync_outbox",
    indices = [
        Index(value = ["hotelRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["hotelRemoteId", "createdAt"])
    ]
)
data class BookingSyncOutboxEntity(
    @PrimaryKey val operationId: String,
    val hotelRemoteId: String,
    val bookingRemoteId: String,
    val changeSetJson: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null
)

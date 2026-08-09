package com.example.bookingregister.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingSyncOutboxEntity

@Dao
interface BookingSyncOutboxDao {
    @Query("SELECT * FROM booking_sync_outbox WHERE hotelRemoteId = :hotelRemoteId ORDER BY createdAt ASC")
    suspend fun getPending(hotelRemoteId: String): List<BookingSyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: BookingSyncOutboxEntity)

    @Query("DELETE FROM booking_sync_outbox WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Query("DELETE FROM booking_sync_outbox WHERE hotelRemoteId = :hotelRemoteId AND bookingRemoteId = :bookingRemoteId")
    suspend fun deleteForBooking(hotelRemoteId: String, bookingRemoteId: String)

    @Query("""
        UPDATE booking_sync_outbox
        SET attemptCount = attemptCount + 1, lastError = :lastError
        WHERE operationId = :operationId
    """)
    suspend fun markFailed(operationId: String, lastError: String)

    @Query("SELECT COUNT(*) FROM booking_sync_outbox WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countPending(hotelRemoteId: String): Int

    @Query("SELECT COUNT(*) FROM booking_sync_outbox WHERE hotelRemoteId = :hotelRemoteId AND bookingRemoteId = :bookingRemoteId")
    suspend fun countPendingForBooking(hotelRemoteId: String, bookingRemoteId: String): Int
}

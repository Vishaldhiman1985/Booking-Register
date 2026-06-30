package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.bookingregister.data.entities.BookingEntity

@Dao
interface BookingDao {

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY checkInMillis ASC
    """)
    fun observeBookings(hotelRemoteId: String): LiveData<List<BookingEntity>>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND checkInMillis < :windowEndMillis
        AND checkOutMillis > :windowStartMillis
        ORDER BY checkInMillis ASC
    """)
    fun observeBookingsForWindow(
        hotelRemoteId: String,
        windowStartMillis: Long,
        windowEndMillis: Long
    ): LiveData<List<BookingEntity>>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
    """)
    suspend fun getBookings(hotelRemoteId: String): List<BookingEntity>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND bookingStatus IN (:statuses)
        ORDER BY checkInMillis ASC
    """)
    suspend fun getBookingsByStatus(
        hotelRemoteId: String,
        statuses: List<String>
    ): List<BookingEntity>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND checkInMillis < :windowEndMillis
        AND checkOutMillis > :windowStartMillis
        ORDER BY checkInMillis ASC
    """)
    suspend fun getBookingsForWindow(
        hotelRemoteId: String,
        windowStartMillis: Long,
        windowEndMillis: Long
    ): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BookingEntity?

    @Query("SELECT * FROM bookings WHERE bookingUuid = :bookingUuid LIMIT 1")
    suspend fun getByBookingUuid(bookingUuid: String): BookingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(booking: BookingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bookings: List<BookingEntity>)

    @Query("""
        UPDATE bookings 
        SET isDeleted = 1, updatedAt = :updatedAt 
        WHERE remoteId = :remoteId
    """)
    suspend fun softDelete(remoteId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND checkInMillis < :checkOutMillis
        AND checkOutMillis > :checkInMillis
    """)
    suspend fun getOverlappingBookings(
        hotelRemoteId: String,
        checkInMillis: Long,
        checkOutMillis: Long
    ): List<BookingEntity>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND bookingStatus = :bookingStatus
        AND checkInMillis < :checkOutMillis
        AND checkOutMillis > :checkInMillis
    """)
    suspend fun getOverlappingBookingsByStatus(
        hotelRemoteId: String,
        bookingStatus: String,
        checkInMillis: Long,
        checkOutMillis: Long
    ): List<BookingEntity>

    @Query("""
        UPDATE bookings
        SET bookingStatus = :bookingStatus,
            actualCheckInAt = :actualCheckInAt,
            actualCheckOutAt = :actualCheckOutAt,
            checkoutNote = :checkoutNote,
            reopenNote = :reopenNote,
            reopenedAt = :reopenedAt,
            updatedAt = :updatedAt,
            syncState = 'PENDING',
            lastSyncError = NULL,
            baseRevision = CASE WHEN baseRevision > 0 THEN baseRevision ELSE revision END
        WHERE remoteId = :remoteId
    """)
    suspend fun updateLifecycleStatus(
        remoteId: String,
        bookingStatus: String,
        actualCheckInAt: Long?,
        actualCheckOutAt: Long?,
        checkoutNote: String?,
        reopenNote: String?,
        reopenedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState IN ('PENDING', 'FAILED')
    """)
    suspend fun getUnsyncedBookings(hotelRemoteId: String): List<BookingEntity>

    @Query("""
        SELECT * FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState IN ('PENDING', 'FAILED')
    """)
    fun observeUnsyncedBookings(hotelRemoteId: String): LiveData<List<BookingEntity>>

    @Query("""
        SELECT COALESCE(SUM(balance), 0.0) FROM bookings
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND balance > 0
    """)
    fun observeOutstandingBalance(hotelRemoteId: String): LiveData<Double>

    @Query("SELECT COUNT(*) FROM bookings WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllBookings(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM bookings WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?
}

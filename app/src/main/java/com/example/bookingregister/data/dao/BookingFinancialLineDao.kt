package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingFinancialLineEntity

@Dao
interface BookingFinancialLineDao {
    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, bookingRemoteId ASC, roomRemoteId ASC
    """)
    fun observeLines(hotelRemoteId: String): LiveData<List<BookingFinancialLineEntity>>

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, bookingRemoteId ASC, roomRemoteId ASC
    """)
    suspend fun getLines(hotelRemoteId: String): List<BookingFinancialLineEntity>
    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    fun observeLinesForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): LiveData<List<BookingFinancialLineEntity>>

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    suspend fun getLinesForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): List<BookingFinancialLineEntity>

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId AND bookingRemoteId = :bookingRemoteId
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    suspend fun getAllLinesForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): List<BookingFinancialLineEntity>

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    fun observeUnsyncedLines(hotelRemoteId: String): LiveData<List<BookingFinancialLineEntity>>

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedLines(hotelRemoteId: String): List<BookingFinancialLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: BookingFinancialLineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lines: List<BookingFinancialLineEntity>)

    @Query("""
        SELECT COUNT(*) FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
    """)
    suspend fun countAllLines(hotelRemoteId: String): Int

    @Query("""
        SELECT COUNT(*) FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId AND roomRemoteId = :roomRemoteId
    """)
    suspend fun countForRoom(hotelRemoteId: String, roomRemoteId: String): Int

    @Query("""
        SELECT MAX(updatedAt) FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
    """)
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE remoteId = :remoteId
        LIMIT 1
    """)
    suspend fun getByRemoteId(remoteId: String): BookingFinancialLineEntity?

    @Query("""
        SELECT * FROM booking_financial_lines
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND roomRemoteId = :roomRemoteId
        AND businessDateMillis = :businessDateMillis
        LIMIT 1
    """)
    suspend fun getByRoomNight(
        hotelRemoteId: String,
        bookingRemoteId: String,
        roomRemoteId: String,
        businessDateMillis: Long
    ): BookingFinancialLineEntity?

    @Query("DELETE FROM booking_financial_lines WHERE localId = :localId")
    suspend fun hardDeleteByLocalId(localId: Long)
}

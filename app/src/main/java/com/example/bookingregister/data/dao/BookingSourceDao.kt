package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingSourceEntity

@Dao
interface BookingSourceDao {
    @Query("""
        SELECT * FROM booking_sources
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY isActive DESC, sourceName COLLATE NOCASE ASC
    """)
    fun observeSources(hotelRemoteId: String): LiveData<List<BookingSourceEntity>>

    @Query("""
        SELECT * FROM booking_sources
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY isActive DESC, sourceName COLLATE NOCASE ASC
    """)
    suspend fun getSources(hotelRemoteId: String): List<BookingSourceEntity>

    @Query("""
        SELECT * FROM booking_sources
        WHERE hotelRemoteId = :hotelRemoteId
        AND propertyRemoteId = :propertyRemoteId
        AND isDeleted = 0
        ORDER BY isActive DESC, sourceName COLLATE NOCASE ASC
    """)
    suspend fun getSourcesForProperty(hotelRemoteId: String, propertyRemoteId: String): List<BookingSourceEntity>

    @Query("SELECT * FROM booking_sources WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BookingSourceEntity?

    @Query("""
        SELECT * FROM booking_sources
        WHERE hotelRemoteId = :hotelRemoteId
        AND (
            (:propertyRemoteId IS NULL AND propertyRemoteId IS NULL)
            OR propertyRemoteId = :propertyRemoteId
        )
        AND lower(sourceName) = lower(:sourceName)
        LIMIT 1
    """)
    suspend fun getByName(hotelRemoteId: String, sourceName: String, propertyRemoteId: String?): BookingSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: BookingSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sources: List<BookingSourceEntity>)

    @Query("""
        SELECT * FROM booking_sources
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState IN ('PENDING', 'FAILED')
    """)
    suspend fun getUnsyncedSources(hotelRemoteId: String): List<BookingSourceEntity>

    @Query("SELECT COUNT(*) FROM booking_sources WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllSources(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM booking_sources WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?
}

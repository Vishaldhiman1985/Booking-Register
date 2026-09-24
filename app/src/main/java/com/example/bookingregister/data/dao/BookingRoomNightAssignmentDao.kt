package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity

@Dao
interface BookingRoomNightAssignmentDao {

    @Query("""
        SELECT * FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    fun observeForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): LiveData<List<BookingRoomNightAssignmentEntity>>

    @Query("""
        SELECT * FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    suspend fun getForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): List<BookingRoomNightAssignmentEntity>

    @Query("""
        SELECT * FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND businessDateMillis >= :startMillis
        AND businessDateMillis < :endMillis
        ORDER BY businessDateMillis ASC, roomRemoteId ASC
    """)
    suspend fun getForWindow(
        hotelRemoteId: String,
        startMillis: Long,
        endMillis: Long
    ): List<BookingRoomNightAssignmentEntity>

    @Query("""
        SELECT * FROM booking_room_night_assignments
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
    ): BookingRoomNightAssignmentEntity?

    @Query("""
        SELECT * FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    fun observeUnsynced(
        hotelRemoteId: String
    ): LiveData<List<BookingRoomNightAssignmentEntity>>

    @Query("""
        SELECT * FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsynced(
        hotelRemoteId: String
    ): List<BookingRoomNightAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: BookingRoomNightAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<BookingRoomNightAssignmentEntity>)

    @Query("""
        DELETE FROM booking_room_night_assignments
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
    """)
    suspend fun hardDeleteForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    )
}
package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.bookingregister.data.entities.RoomEntity

@Dao
interface RoomDao {

    @Query("""
        SELECT * FROM rooms 
        WHERE hotelRemoteId = :hotelRemoteId 
        AND isDeleted = 0 
        ORDER BY categorySortOrder ASC, categoryName ASC, sortOrder ASC, roomName ASC
    """)
    fun observeRooms(hotelRemoteId: String): LiveData<List<RoomEntity>>

    @Query("""
        SELECT * FROM rooms 
        WHERE hotelRemoteId = :hotelRemoteId 
        AND isDeleted = 0 
        ORDER BY categorySortOrder ASC, categoryName ASC, sortOrder ASC, roomName ASC
    """)

    suspend fun getRooms(hotelRemoteId: String): List<RoomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rooms: List<RoomEntity>)

    @Query("SELECT * FROM rooms WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): RoomEntity?

    @Query("""
        SELECT * FROM rooms 
        WHERE hotelRemoteId = :hotelRemoteId 
        AND lower(roomName) = lower(:roomName)
        LIMIT 1
    """)
    suspend fun getByRoomName(hotelRemoteId: String, roomName: String): RoomEntity?
    @Query("SELECT COUNT(*) FROM rooms WHERE hotelRemoteId = :hotelRemoteId AND isDeleted = 0")
    suspend fun countRooms(hotelRemoteId: String): Int

    @Query("""
        SELECT MAX(sortOrder) FROM rooms
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND lower(categoryName) = lower(:categoryName)
    """)
    suspend fun maxSortOrderForCategory(hotelRemoteId: String, categoryName: String): Int?

    @Query("""
        SELECT categorySortOrder FROM rooms
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND lower(categoryName) = lower(:categoryName)
        ORDER BY categorySortOrder ASC
        LIMIT 1
    """)
    suspend fun categorySortOrder(hotelRemoteId: String, categoryName: String): Int?

    @Query("""
        SELECT COUNT(DISTINCT lower(categoryName)) FROM rooms
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
    """)
    suspend fun countCategories(hotelRemoteId: String): Int

    @Query("""
        SELECT * FROM rooms
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState IN ('PENDING', 'FAILED')
    """)
    suspend fun getUnsyncedRooms(hotelRemoteId: String): List<RoomEntity>

    @Query("""
        UPDATE rooms 
        SET isDeleted = 1, updatedAt = :updatedAt 
        WHERE remoteId = :remoteId
    """)
    suspend fun softDelete(remoteId: String, updatedAt: Long = System.currentTimeMillis())
    @Query("SELECT COUNT(*) FROM rooms WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllRooms(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM rooms WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?
}

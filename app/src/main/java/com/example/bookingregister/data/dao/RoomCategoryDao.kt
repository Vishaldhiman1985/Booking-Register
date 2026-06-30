package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.RoomCategoryEntity

@Dao
interface RoomCategoryDao {

    @Query("""
        SELECT * FROM room_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY sortOrder ASC, categoryName ASC
    """)
    fun observeCategories(hotelRemoteId: String): LiveData<List<RoomCategoryEntity>>

    @Query("""
        SELECT * FROM room_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY sortOrder ASC, categoryName ASC
    """)
    suspend fun getCategories(hotelRemoteId: String): List<RoomCategoryEntity>

    @Query("SELECT * FROM room_categories WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): RoomCategoryEntity?

    @Query("""
        SELECT * FROM room_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND lower(categoryName) = lower(:categoryName)
        LIMIT 1
    """)
    suspend fun getByName(hotelRemoteId: String, categoryName: String): RoomCategoryEntity?

    @Query("""
        SELECT COUNT(*) FROM room_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
    """)
    suspend fun countCategories(hotelRemoteId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: RoomCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<RoomCategoryEntity>)

    @Query("""
        SELECT * FROM room_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState IN ('PENDING', 'FAILED')
    """)
    suspend fun getUnsyncedCategories(hotelRemoteId: String): List<RoomCategoryEntity>
    @Query("SELECT COUNT(*) FROM room_categories WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllCategories(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM room_categories WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?
}

package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.FoodMenuItemEntity

@Dao
interface FoodMenuItemDao {
    @Query("""
        SELECT * FROM food_menu_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY categoryName COLLATE NOCASE, itemName COLLATE NOCASE
    """)
    fun observeMenuItems(hotelRemoteId: String): LiveData<List<FoodMenuItemEntity>>

    @Query("""
        SELECT * FROM food_menu_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedItems(hotelRemoteId: String): List<FoodMenuItemEntity>

    @Query("SELECT * FROM food_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun getItems(hotelRemoteId: String): List<FoodMenuItemEntity>

    @Query("SELECT * FROM food_menu_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FoodMenuItemEntity?

    @Query("SELECT COUNT(*) FROM food_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllItems(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM food_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FoodMenuItemEntity)
}

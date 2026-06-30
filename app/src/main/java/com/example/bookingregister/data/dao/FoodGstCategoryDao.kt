package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.FoodGstCategoryEntity

@Dao
interface FoodGstCategoryDao {
    @Query("""
        SELECT * FROM food_gst_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY isDefault DESC, categoryName COLLATE NOCASE
    """)
    fun observeCategories(hotelRemoteId: String): LiveData<List<FoodGstCategoryEntity>>

    @Query("""
        SELECT * FROM food_gst_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY isDefault DESC, categoryName COLLATE NOCASE
    """)
    suspend fun getCategories(hotelRemoteId: String): List<FoodGstCategoryEntity>

    @Query("""
        SELECT * FROM food_gst_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDefault = 1
        AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun getDefaultCategory(hotelRemoteId: String): FoodGstCategoryEntity?

    @Query("""
        SELECT * FROM food_gst_categories
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedCategories(hotelRemoteId: String): List<FoodGstCategoryEntity>

    @Query("SELECT * FROM food_gst_categories WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FoodGstCategoryEntity?

    @Query("SELECT COUNT(*) FROM food_gst_categories WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllCategories(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM food_gst_categories WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: FoodGstCategoryEntity)
}

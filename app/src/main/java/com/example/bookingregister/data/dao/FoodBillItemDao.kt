package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.FoodBillItemEntity

@Dao
interface FoodBillItemDao {
    @Query("""
        SELECT * FROM food_bill_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY orderMillis ASC, itemName COLLATE NOCASE
    """)
    fun observeItems(hotelRemoteId: String): LiveData<List<FoodBillItemEntity>>

    @Query("""
        SELECT * FROM food_bill_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND billRemoteId = :billRemoteId
        AND isDeleted = 0
        ORDER BY orderMillis ASC, itemName COLLATE NOCASE
    """)
    suspend fun getItemsForBill(hotelRemoteId: String, billRemoteId: String): List<FoodBillItemEntity>

    @Query("""
        SELECT * FROM food_bill_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND billRemoteId = :billRemoteId
        ORDER BY orderMillis ASC, itemName COLLATE NOCASE
    """)
    suspend fun getAllItemsForBill(hotelRemoteId: String, billRemoteId: String): List<FoodBillItemEntity>

    @Query("""
        SELECT * FROM food_bill_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedItems(hotelRemoteId: String): List<FoodBillItemEntity>

    @Query("SELECT * FROM food_bill_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FoodBillItemEntity?

    @Query("SELECT COUNT(*) FROM food_bill_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllItems(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM food_bill_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FoodBillItemEntity)
}

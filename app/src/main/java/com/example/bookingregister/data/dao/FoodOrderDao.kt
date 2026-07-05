package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.FoodOrderEntity

@Dao
interface FoodOrderDao {
    @Query("""
        SELECT * FROM food_orders
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY orderMillis DESC
    """)
    fun observeOrders(hotelRemoteId: String): LiveData<List<FoodOrderEntity>>

    @Query("""
        SELECT * FROM food_orders
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedOrders(hotelRemoteId: String): List<FoodOrderEntity>

    @Query("SELECT * FROM food_orders WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun getOrders(hotelRemoteId: String): List<FoodOrderEntity>


    @Query("""
        SELECT * FROM food_orders
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY orderMillis ASC
    """)
    suspend fun getOrdersForBooking(hotelRemoteId: String, bookingRemoteId: String): List<FoodOrderEntity>
    @Query("SELECT * FROM food_orders WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FoodOrderEntity?

    @Query("SELECT * FROM food_orders WHERE remoteId IN (:remoteIds)")
    suspend fun getByRemoteIds(remoteIds: List<String>): List<FoodOrderEntity>

    @Query("SELECT COUNT(*) FROM food_orders WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllOrders(hotelRemoteId: String): Int

    @Query("""
        SELECT COUNT(*) FROM food_orders
        WHERE hotelRemoteId = :hotelRemoteId AND roomRemoteId = :roomRemoteId
    """)
    suspend fun countForRoom(hotelRemoteId: String, roomRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM food_orders WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: FoodOrderEntity)
}

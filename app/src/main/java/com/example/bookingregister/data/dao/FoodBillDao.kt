package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.FoodBillEntity

@Dao
interface FoodBillDao {
    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY billMillis DESC
    """)
    fun observeBills(hotelRemoteId: String): LiveData<List<FoodBillEntity>>

    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND billMillis >= :startMillis
        AND billMillis < :endMillis
        ORDER BY billMillis DESC
    """)
    fun observeBillsForRange(
        hotelRemoteId: String,
        startMillis: Long,
        endMillis: Long
    ): LiveData<List<FoodBillEntity>>

    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND billMillis < :beforeMillis
        ORDER BY billMillis DESC
        LIMIT :limit
    """)
    suspend fun getArchivedBillsBefore(
        hotelRemoteId: String,
        beforeMillis: Long,
        limit: Int
    ): List<FoodBillEntity>

    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND (
            billNumber LIKE '%' || :query || '%'
            OR guestName LIKE '%' || :query || '%'
            OR roomsIncluded LIKE '%' || :query || '%'
            OR CAST(grandTotal AS TEXT) LIKE '%' || :query || '%'
            OR CAST(billMillis AS TEXT) LIKE '%' || :query || '%'
        )
        ORDER BY billMillis DESC
        LIMIT :limit
    """)
    suspend fun searchBills(
        hotelRemoteId: String,
        query: String,
        limit: Int
    ): List<FoodBillEntity>

    @Query("SELECT * FROM food_bills WHERE hotelRemoteId = :hotelRemoteId ORDER BY billMillis DESC")
    suspend fun getBills(hotelRemoteId: String): List<FoodBillEntity>

    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedBills(hotelRemoteId: String): List<FoodBillEntity>

    @Query("SELECT * FROM food_bills WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FoodBillEntity?

    @Query("""
        SELECT billNumber FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND billNumber LIKE :seriesPrefix || '%'
        ORDER BY billNumber DESC
    """)
    suspend fun getBillNumbersForSeries(
        hotelRemoteId: String,
        seriesPrefix: String
    ): List<String>

    
    @Query("""
        SELECT * FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND remoteId LIKE :remoteIdPrefix || '%'
        AND isDeleted = 0
        ORDER BY billMillis DESC
        LIMIT 1
    """)
    suspend fun getFinalBillForBooking(hotelRemoteId: String, remoteIdPrefix: String): FoodBillEntity?

    @Query("SELECT COUNT(*) FROM food_bills WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllBills(hotelRemoteId: String): Int

    @Query("""
        SELECT COUNT(*) FROM food_bills
        WHERE hotelRemoteId = :hotelRemoteId
        AND propertyRemoteId = :propertyRemoteId
        AND isDeleted = 0
    """)
    suspend fun countBillsForProperty(
        hotelRemoteId: String,
        propertyRemoteId: String
    ): Int

    @Query("SELECT MAX(updatedAt) FROM food_bills WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: FoodBillEntity)
}

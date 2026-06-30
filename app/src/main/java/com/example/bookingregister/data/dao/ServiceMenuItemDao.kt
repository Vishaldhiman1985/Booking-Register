package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.ServiceMenuItemEntity

@Dao
interface ServiceMenuItemDao {
    @Query("""
        SELECT * FROM service_menu_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY sortOrder ASC, categoryName COLLATE NOCASE, serviceName COLLATE NOCASE
    """)
    fun observeMenuItems(hotelRemoteId: String): LiveData<List<ServiceMenuItemEntity>>

    @Query("""
        SELECT * FROM service_menu_items
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedItems(hotelRemoteId: String): List<ServiceMenuItemEntity>

    @Query("SELECT * FROM service_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun getItems(hotelRemoteId: String): List<ServiceMenuItemEntity>

    @Query("SELECT * FROM service_menu_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ServiceMenuItemEntity?

    @Query("SELECT COUNT(*) FROM service_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllItems(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM service_menu_items WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ServiceMenuItemEntity)
}

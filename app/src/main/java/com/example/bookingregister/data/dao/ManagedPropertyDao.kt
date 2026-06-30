package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.ManagedPropertyEntity

@Dao
interface ManagedPropertyDao {
    @Query(
        """
        SELECT * FROM managed_properties
        WHERE hotelRemoteId = :hotelRemoteId AND isDeleted = 0
        ORDER BY sortOrder ASC, propertyName COLLATE NOCASE ASC
        """
    )
    fun observeProperties(hotelRemoteId: String): LiveData<List<ManagedPropertyEntity>>

    @Query(
        """
        SELECT * FROM managed_properties
        WHERE hotelRemoteId = :hotelRemoteId AND isDeleted = 0
        ORDER BY sortOrder ASC, propertyName COLLATE NOCASE ASC
        """
    )
    suspend fun getProperties(hotelRemoteId: String): List<ManagedPropertyEntity>

    @Query("SELECT * FROM managed_properties WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ManagedPropertyEntity?

    @Query(
        """
        SELECT * FROM managed_properties
        WHERE hotelRemoteId = :hotelRemoteId AND lower(propertyName) = lower(:propertyName)
        LIMIT 1
        """
    )
    suspend fun getByName(hotelRemoteId: String, propertyName: String): ManagedPropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(property: ManagedPropertyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(properties: List<ManagedPropertyEntity>)

    @Query(
        """
        SELECT * FROM managed_properties
        WHERE hotelRemoteId = :hotelRemoteId AND syncState != 'SYNCED'
        """
    )
    suspend fun getUnsyncedProperties(hotelRemoteId: String): List<ManagedPropertyEntity>

    @Query("SELECT COUNT(*) FROM managed_properties WHERE hotelRemoteId = :hotelRemoteId AND isDeleted = 0")
    suspend fun countProperties(hotelRemoteId: String): Int

    @Query("SELECT COUNT(*) FROM managed_properties WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllProperties(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM managed_properties WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?
}

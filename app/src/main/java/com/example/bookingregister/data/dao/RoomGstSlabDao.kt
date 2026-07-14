package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.RoomGstSlabEntity

@Dao
interface RoomGstSlabDao {

    @Query("""
        SELECT * FROM room_gst_slabs
        WHERE hotelRemoteId = :hotelRemoteId
        ORDER BY minGrossAmount ASC
    """)
    fun observeSlabs(hotelRemoteId: String): LiveData<List<RoomGstSlabEntity>>

    @Query("""
        SELECT * FROM room_gst_slabs
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        AND isActive = 1
        AND effectiveFromMillis <= :bookingMillis
        AND (effectiveToMillis IS NULL OR effectiveToMillis >= :bookingMillis)
        ORDER BY minGrossAmount ASC
    """)
    suspend fun getActiveSlabs(
        hotelRemoteId: String,
        bookingMillis: Long
    ): List<RoomGstSlabEntity>


    @Query("SELECT * FROM room_gst_slabs WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): RoomGstSlabEntity?

    @Query("SELECT * FROM room_gst_slabs WHERE hotelRemoteId = :hotelRemoteId AND syncState != 'SYNCED' ORDER BY updatedAt ASC")
    suspend fun getUnsyncedSlabs(hotelRemoteId: String): List<RoomGstSlabEntity>

    @Query("SELECT COUNT(*) FROM room_gst_slabs WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAll(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM room_gst_slabs WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slab: RoomGstSlabEntity)

    @Query("""
        UPDATE room_gst_slabs
        SET isDeleted = 1,
            syncState = 'PENDING',
            updatedAt = :now
        WHERE remoteId = :remoteId
    """)
    suspend fun softDelete(remoteId: String, now: Long)
}
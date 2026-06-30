package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.HotelEntity

@Dao
interface HotelDao {

    @Query("SELECT * FROM hotels WHERE remoteId = :remoteId LIMIT 1")
    fun observeHotel(remoteId: String): LiveData<HotelEntity?>

    @Query("SELECT * FROM hotels WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): HotelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hotel: HotelEntity)

    @Query("""
        SELECT * FROM hotels
        WHERE syncState IN ('PENDING', 'FAILED')
    """)
    suspend fun getUnsyncedHotels(): List<HotelEntity>
}


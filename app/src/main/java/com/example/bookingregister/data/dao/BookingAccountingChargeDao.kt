package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity

@Dao
interface BookingAccountingChargeDao {
    @Query("""
        SELECT * FROM booking_accounting_charges
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY chargeMillis ASC, updatedAt ASC
    """)
    fun observeCharges(hotelRemoteId: String): LiveData<List<BookingAccountingChargeEntity>>

    @Query("""
        SELECT * FROM booking_accounting_charges
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY chargeMillis ASC, updatedAt ASC
    """)
    suspend fun getChargesForBooking(
        hotelRemoteId: String,
        bookingRemoteId: String
    ): List<BookingAccountingChargeEntity>

    @Query("""
        SELECT * FROM booking_accounting_charges
        WHERE hotelRemoteId = :hotelRemoteId
        AND linkedFinalBillId = :billRemoteId
        ORDER BY chargeMillis ASC, updatedAt ASC
    """)
    suspend fun getChargesForFinalBill(
        hotelRemoteId: String,
        billRemoteId: String
    ): List<BookingAccountingChargeEntity>

    @Query("""
        SELECT * FROM booking_accounting_charges
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    fun observeUnsyncedCharges(hotelRemoteId: String): LiveData<List<BookingAccountingChargeEntity>>

    @Query("""
        SELECT * FROM booking_accounting_charges
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    suspend fun getUnsyncedCharges(hotelRemoteId: String): List<BookingAccountingChargeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(charge: BookingAccountingChargeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(charges: List<BookingAccountingChargeEntity>)

    @Query("SELECT COUNT(*) FROM booking_accounting_charges WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun countAllCharges(hotelRemoteId: String): Int

    @Query("SELECT MAX(updatedAt) FROM booking_accounting_charges WHERE hotelRemoteId = :hotelRemoteId")
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Query("SELECT * FROM booking_accounting_charges WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BookingAccountingChargeEntity?
}

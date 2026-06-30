package com.example.bookingregister.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bookingregister.data.entities.BookingPaymentEntity

@Dao
interface BookingPaymentDao {
    @Query("""
        SELECT * FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
        AND isDeleted = 0
        ORDER BY paymentMillis ASC, updatedAt ASC
    """)
    fun observePayments(hotelRemoteId: String): LiveData<List<BookingPaymentEntity>>

    @Query("""
        SELECT * FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
        ORDER BY updatedAt ASC
    """)
    fun observeUnsyncedPayments(hotelRemoteId: String): LiveData<List<BookingPaymentEntity>>

    @Query("""
        SELECT * FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
        ORDER BY paymentMillis ASC, updatedAt ASC
    """)
    suspend fun getPaymentsForBooking(hotelRemoteId: String, bookingRemoteId: String): List<BookingPaymentEntity>

    @Query("""
        SELECT * FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
    """)
    suspend fun getPayments(hotelRemoteId: String): List<BookingPaymentEntity>

    @Query("""
        SELECT COUNT(*) FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
        AND bookingRemoteId = :bookingRemoteId
        AND isDeleted = 0
    """)
    suspend fun countPaymentsForBooking(hotelRemoteId: String, bookingRemoteId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: BookingPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<BookingPaymentEntity>)

    @Query("""
        SELECT * FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
        AND syncState != 'SYNCED'
    """)
    suspend fun getUnsyncedPayments(hotelRemoteId: String): List<BookingPaymentEntity>

    @Query("""
        SELECT COUNT(*) FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
    """)
    suspend fun countAllPayments(hotelRemoteId: String): Int

    @Query("""
        SELECT MAX(updatedAt) FROM booking_payments
        WHERE hotelRemoteId = :hotelRemoteId
    """)
    suspend fun maxUpdatedAt(hotelRemoteId: String): Long?

    @Query("""
        SELECT * FROM booking_payments
        WHERE remoteId = :remoteId
        LIMIT 1
    """)
    suspend fun getByRemoteId(remoteId: String): BookingPaymentEntity?
}

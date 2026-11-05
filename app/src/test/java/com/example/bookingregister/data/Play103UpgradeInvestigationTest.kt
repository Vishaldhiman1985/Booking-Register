package com.example.bookingregister.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bookingregister.data.sync.LegacyPlay103Recovery
import com.example.bookingregister.data.sync.LegacyRecordPresence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test-only reproduction using the exact Room v11 CREATE statements extracted
 * from the production 1.0.3 (versionCode 4) Play bundle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Play103UpgradeInvestigationTest {
    @Test
    fun currentDatabaseDoesNotEnterLegacyRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val unexpectedCloudCall = object : LegacyRecordPresence {
                override suspend fun existingLegacyRecordIds(
                    collection: String, remoteIds: List<String>
                ): Set<String> = error("Current installs must not query legacy recovery")
            }
            val result = runBlocking {
                LegacyPlay103Recovery(current, unexpectedCloudCall, "hotel-test")
                    .stageMissingRecords()
            }
            assertEquals(0, result.bookings)
            assertEquals(0, result.payments)
        } finally {
            current.close()
        }
    }

    @Test
    fun play103DatabaseMigratesTo42AndKeepsBookingsPaymentsAndSyncStates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "play_103_upgrade_investigation.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val schema = javaClass.classLoader!!
                .getResourceAsStream("play_1_0_3_v11_schema.sql")!!
                .bufferedReader().readLines()
            schema.forEach(old::execSQL)
            old.insertOrThrow("hotels", null, ContentValues().apply {
                put("remoteId", "hotel-test")
                put("hotelName", "Test hotel")
                put("updatedAt", 1000L)
                put("isDeleted", 0)
                put("syncState", "SYNCED")
                put("revision", 1)
                put("baseRevision", 1)
            })
            insertBooking(old, "already-synced-but-cloud-missing", "SYNCED", 3500.0, 500.0)
            insertBooking(old, "failed-upload", "FAILED", 4200.0, 1200.0)
            insertBooking(old, "pending-upload", "PENDING", 2500.0, 0.0)
            insertBooking(old, "taxed-booking", "SYNCED", 1050.0, 0.0)
            old.execSQL(
                "UPDATE bookings SET roomRevenue = 1000.0, propertyTax = 50.0 WHERE remoteId = 'taxed-booking'"
            )
            insertPayment(old, "payment-synced", "already-synced-but-cloud-missing", "SYNCED", 500.0)
            insertPayment(old, "payment-failed", "failed-upload", "FAILED", 1200.0)
            old.insertOrThrow("booking_sources", null, ContentValues().apply {
                put("remoteId", "source-ota")
                put("hotelRemoteId", "hotel-test")
                put("sourceName", "OTA")
                put("sourceType", "OTA")
                put("commissionPercent", 18.0)
                put("commissionGstPercent", 18.0)
                put("tcsPercent", 1.0)
                put("tdsPercent", 0.5)
                put("fixedFee", 0.0)
                put("isActive", 1)
                put("updatedAt", 1000L)
                put("isDeleted", 0)
                put("syncState", "SYNCED")
                put("revision", 1)
                put("baseRevision", 1)
            })
            old.version = 11
        }

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.allMigrations())
            .allowMainThreadQueries()
            .build()
        try {
            val db = upgraded.openHelper.writableDatabase
            assertEquals(42, db.version)
            db.query(
                "SELECT remoteId, syncState, receivable, paid, balance FROM bookings ORDER BY remoteId"
            ).use { rows ->
                assertEquals(4, rows.count)
                assertTrue(rows.moveToFirst())
                assertEquals("already-synced-but-cloud-missing", rows.getString(0))
                assertEquals("SYNCED", rows.getString(1))
                assertEquals(3500.0, rows.getDouble(2), 0.001)
                assertEquals(500.0, rows.getDouble(3), 0.001)
                assertEquals(3000.0, rows.getDouble(4), 0.001)
                assertTrue(rows.moveToNext())
                assertEquals("failed-upload", rows.getString(0))
                assertEquals("FAILED", rows.getString(1))
                assertTrue(rows.moveToNext())
                assertEquals("pending-upload", rows.getString(0))
                assertEquals("PENDING", rows.getString(1))
                assertTrue(rows.moveToNext())
                assertEquals("taxed-booking", rows.getString(0))
                assertEquals("SYNCED", rows.getString(1))
                assertFalse(rows.moveToNext())
            }
            db.query("SELECT remoteId, syncState, amount FROM booking_payments ORDER BY remoteId").use {
                assertEquals(2, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("payment-failed", it.getString(0))
                assertEquals("FAILED", it.getString(1))
                assertEquals(1200.0, it.getDouble(2), 0.001)
                assertTrue(it.moveToNext())
                assertEquals("payment-synced", it.getString(0))
                assertEquals("SYNCED", it.getString(1))
                assertEquals(500.0, it.getDouble(2), 0.001)
            }
            db.query("SELECT COUNT(*) FROM booking_sync_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM booking_financial_lines").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT commissionPercent, tcsPercent FROM booking_sources WHERE remoteId = 'source-ota'").use {
                assertTrue(it.moveToFirst())
                assertEquals(18.0, it.getDouble(0), 0.001)
                assertEquals(1.0, it.getDouble(1), 0.001)
            }
            db.query("SELECT remoteId, syncState, receivable, paid FROM legacy_v11_backup_bookings ORDER BY remoteId").use {
                assertEquals(4, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("already-synced-but-cloud-missing", it.getString(0))
                assertEquals("SYNCED", it.getString(1))
                assertEquals(3500.0, it.getDouble(2), 0.001)
                assertEquals(500.0, it.getDouble(3), 0.001)
            }
            db.query("SELECT remoteId, amount FROM legacy_v11_backup_booking_payments ORDER BY remoteId").use {
                assertEquals(2, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("payment-failed", it.getString(0))
                assertEquals(1200.0, it.getDouble(1), 0.001)
            }
            val retryCandidates = runBlocking {
                upgraded.bookingDao().getUnsyncedBookings("hotel-test")
            }
            assertEquals(
                setOf("failed-upload", "pending-upload"),
                retryCandidates.map { it.remoteId }.toSet()
            )

            val failedCloudRead = object : LegacyRecordPresence {
                override suspend fun existingLegacyRecordIds(
                    collection: String, remoteIds: List<String>
                ): Set<String> = error("Server unavailable")
            }
            runBlocking {
                runCatching {
                    LegacyPlay103Recovery(upgraded, failedCloudRead, "hotel-test")
                        .stageMissingRecords()
                }.onSuccess { error("Recovery must fail closed when the server is unavailable") }
            }
            db.query("SELECT COUNT(*) FROM legacy_v11_recovery_stage").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }

            var serverReads = 0
            val cloud = object : LegacyRecordPresence {
                override suspend fun existingLegacyRecordIds(
                    collection: String, remoteIds: List<String>
                ): Set<String> = when (collection) {
                    // A second recovery pass should use the durable stage markers.
                    "bookings" -> setOf("failed-upload")
                    "bookingPayments" -> setOf("payment-failed")
                    else -> error("Unexpected collection")
                }.also { serverReads++ }
            }
            val recovery = LegacyPlay103Recovery(upgraded, cloud, "hotel-test")
            val first = runBlocking { recovery.stageMissingRecords() }
            assertEquals(2, first.bookings)
            assertEquals(1, first.payments)
            assertEquals(0, runBlocking { recovery.stageMissingRecords() }.bookings)
            assertEquals(0, runBlocking { recovery.stageMissingRecords() }.payments)
            assertEquals(2, serverReads)
            db.query("SELECT bookingRemoteId FROM booking_sync_outbox ORDER BY bookingRemoteId").use {
                assertEquals(2, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("already-synced-but-cloud-missing", it.getString(0))
                assertTrue(it.moveToNext())
                assertEquals("pending-upload", it.getString(0))
            }
            db.query("SELECT syncState FROM bookings WHERE remoteId = 'failed-upload'").use {
                assertTrue(it.moveToFirst())
                assertEquals("FAILED", it.getString(0))
            }
            db.query("SELECT syncState, lastSyncError FROM bookings WHERE remoteId = 'taxed-booking'").use {
                assertTrue(it.moveToFirst())
                assertEquals("FAILED", it.getString(0))
                assertTrue(it.getString(1).contains("tax breakdown"))
            }
            db.query("SELECT syncState, baseRevision FROM booking_payments WHERE remoteId = 'payment-synced'").use {
                assertTrue(it.moveToFirst())
                assertEquals("PENDING", it.getString(0))
                assertEquals(0, it.getLong(1))
            }
            db.query("SELECT syncState FROM booking_payments WHERE remoteId = 'payment-failed'").use {
                assertTrue(it.moveToFirst())
                assertEquals("FAILED", it.getString(0))
            }
            db.query("SELECT syncState FROM legacy_v11_backup_bookings WHERE remoteId = 'already-synced-but-cloud-missing'").use {
                assertTrue(it.moveToFirst())
                assertEquals("SYNCED", it.getString(0))
            }
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    private fun insertBooking(
        db: SQLiteDatabase, id: String, state: String, receivable: Double, paid: Double
    ) {
        db.insertOrThrow("bookings", null, ContentValues().apply {
            put("remoteId", id)
            put("bookingUuid", "uuid-$id")
            put("hotelRemoteId", "hotel-test")
            put("guestName", "Guest $id")
            put("sourceType", "DIRECT")
            put("adultCount", 2)
            put("childCount", 0)
            put("checkInMillis", 1800000000000L)
            put("checkOutMillis", 1800086400000L)
            put("roomRemoteIds", "[\"room-1\"]")
            put("rate", receivable)
            put("receivable", receivable)
            put("paid", paid)
            put("balance", receivable - paid)
            put("paymentStatus", if (paid > 0) "PARTIAL_PAID" else "NOT_PAID")
            put("grossCharges", receivable)
            put("roomRevenue", receivable)
            put("propertyTax", 0.0)
            put("commissionAmount", 0.0)
            put("commissionTax", 0.0)
            put("sourceFee", 0.0)
            put("tdsAmount", 0.0)
            put("tcsAmount", 0.0)
            put("expectedPayout", receivable)
            put("updatedAt", 1000L)
            put("isDeleted", 0)
            put("syncState", state)
            put("revision", 1)
            put("baseRevision", 0)
        })
    }

    private fun insertPayment(
        db: SQLiteDatabase, id: String, bookingId: String, state: String, amount: Double
    ) {
        db.insertOrThrow("booking_payments", null, ContentValues().apply {
            put("remoteId", id)
            put("hotelRemoteId", "hotel-test")
            put("bookingRemoteId", bookingId)
            put("paymentType", "PAYMENT")
            put("amount", amount)
            put("paymentMillis", 1000L)
            put("updatedAt", 1000L)
            put("isDeleted", 0)
            put("syncState", state)
            put("revision", 1)
            put("baseRevision", 0)
        })
    }
}

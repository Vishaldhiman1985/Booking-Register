package com.example.bookingregister.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseRecentMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun version35MigratesTo40WithoutDataDestruction() {
        migrateEmptyDatabaseFrom(35)
    }

    @Test
    fun version36PreservesLegacyBookingOutboxForSafeRetry() {
        val name = databaseName("v36_outbox")
        helper.createDatabase(name, 36).apply {
            execSQL(
                """
                INSERT INTO booking_sync_outbox (
                    operationId, hotelRemoteId, bookingRemoteId,
                    createdAt, attemptCount, lastError
                ) VALUES ('legacy-op', 'hotel-1', 'booking-1', 100, 2, 'offline')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 40, true, *AppDatabase.allMigrations()).use { db ->
            db.query(
                "SELECT operationId, changeSetJson, attemptCount, lastError FROM booking_sync_outbox"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-op", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(2, cursor.getInt(2))
                assertEquals("offline", cursor.getString(3))
            }
        }
    }

    @Test
    fun version37ArchivesDuplicateRoomNightAndKeepsBestRow() {
        val name = databaseName("v37_duplicates")
        helper.createDatabase(name, 37).apply {
            insertFinancialLine(localId = 1, remoteId = "local-line", revision = 0, updatedAt = 100)
            insertFinancialLine(localId = 2, remoteId = "cloud-line", revision = 2, updatedAt = 200)
            close()
        }

        helper.runMigrationsAndValidate(name, 40, true, *AppDatabase.allMigrations()).use { db ->
            db.query(
                """
                SELECT remoteId FROM booking_financial_lines
                WHERE hotelRemoteId = 'hotel-1'
                  AND bookingRemoteId = 'booking-1'
                  AND roomRemoteId = 'room-1'
                  AND businessDateMillis = 1000
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("cloud-line", cursor.getString(0))
                assertTrue(!cursor.moveToNext())
            }
            db.query("SELECT remoteId FROM booking_financial_line_duplicates_archive").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("local-line", cursor.getString(0))
            }
        }
    }

    @Test
    fun version38MigratesTo40WithoutDataDestruction() {
        migrateEmptyDatabaseFrom(38)
    }

    @Test
    fun version39ClassifiesExistingCancellationsWithoutGuessingNoRefund() {
        val name = databaseName("v39_cancellation")
        helper.createDatabase(name, 39).apply {
            insertBooking("active", "RESERVED", "DIRECT")
            insertBooking("unpaid", "CANCELLED", "DIRECT")
            insertBooking("paid", "CANCELLED", "DIRECT")
            insertBooking("ota", "CANCELLED", "OTA")
            insertPayment("paid-advance", "paid", "ADVANCE", 200.0)
            insertPayment("paid-old-refund", "paid", "REFUND", 25.0)
            close()
        }

        helper.runMigrationsAndValidate(name, 40, true, *AppDatabase.allMigrations()).use { db ->
            val statuses = mutableMapOf<String, Pair<String, Double>>()
            db.query(
                """
                SELECT remoteId, cancellationSettlementStatus, cancellationRefundBaselineAmount
                FROM bookings
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    statuses[cursor.getString(0)] = cursor.getString(1) to cursor.getDouble(2)
                }
            }
            assertEquals("NOT_APPLICABLE", statuses.getValue("active").first)
            assertEquals("NOT_REQUIRED", statuses.getValue("unpaid").first)
            assertEquals("PENDING", statuses.getValue("paid").first)
            assertEquals(25.0, statuses.getValue("paid").second, 0.001)
            assertEquals("PENDING", statuses.getValue("ota").first)
        }
    }

    private fun migrateEmptyDatabaseFrom(version: Int) {
        val name = databaseName("v$version")
        helper.createDatabase(name, version).close()
        helper.runMigrationsAndValidate(name, 40, true, *AppDatabase.allMigrations()).close()
    }

    private fun SupportSQLiteDatabase.insertFinancialLine(
        localId: Long,
        remoteId: String,
        revision: Long,
        updatedAt: Long
    ) {
        val columns = mutableListOf<Column>()
        query("PRAGMA table_info(booking_financial_lines)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns += Column(
                    name = cursor.getString(nameIndex),
                    type = cursor.getString(typeIndex),
                    notNull = cursor.getInt(notNullIndex) == 1
                )
            }
        }
        val values = columns.map { column ->
            when (column.name) {
                "localId" -> localId
                "remoteId" -> remoteId
                "hotelRemoteId" -> "hotel-1"
                "bookingRemoteId" -> "booking-1"
                "roomRemoteId" -> "room-1"
                "businessDateMillis" -> 1000L
                "revision" -> revision
                "updatedAt" -> updatedAt
                "isDeleted" -> 0
                else -> when {
                    !column.notNull -> null
                    column.type.equals("TEXT", ignoreCase = true) -> ""
                    column.type.equals("REAL", ignoreCase = true) -> 0.0
                    else -> 0L
                }
            }
        }
        val names = columns.joinToString(",") { "`${it.name}`" }
        val placeholders = columns.joinToString(",") { "?" }
        execSQL(
            "INSERT INTO booking_financial_lines ($names) VALUES ($placeholders)",
            values.toTypedArray()
        )
    }

    private fun SupportSQLiteDatabase.insertBooking(
        remoteId: String,
        status: String,
        sourceType: String
    ) {
        insertRow(
            table = "bookings",
            values = mapOf(
                "remoteId" to remoteId,
                "bookingUuid" to "uuid-$remoteId",
                "hotelRemoteId" to "hotel-1",
                "guestName" to "Guest $remoteId",
                "checkInMillis" to 1_000L,
                "checkOutMillis" to 2_000L,
                "roomRemoteIds" to "[]",
                "bookingStatus" to status,
                "sourceType" to sourceType,
                "isDeleted" to 0
            )
        )
    }

    private fun SupportSQLiteDatabase.insertPayment(
        remoteId: String,
        bookingRemoteId: String,
        type: String,
        amount: Double
    ) {
        insertRow(
            table = "booking_payments",
            values = mapOf(
                "remoteId" to remoteId,
                "hotelRemoteId" to "hotel-1",
                "bookingRemoteId" to bookingRemoteId,
                "paymentType" to type,
                "amount" to amount,
                "isDeleted" to 0
            )
        )
    }

    private fun SupportSQLiteDatabase.insertRow(table: String, values: Map<String, Any?>) {
        val columns = mutableListOf<Column>()
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns += Column(
                    name = cursor.getString(nameIndex),
                    type = cursor.getString(typeIndex),
                    notNull = cursor.getInt(notNullIndex) == 1
                )
            }
        }
        val rowValues = columns.map { column ->
            values[column.name] ?: when {
                column.name == "localId" -> null
                !column.notNull -> null
                column.type.equals("TEXT", ignoreCase = true) -> ""
                column.type.equals("REAL", ignoreCase = true) -> 0.0
                else -> 0L
            }
        }
        val names = columns.joinToString(",") { "`${it.name}`" }
        val placeholders = columns.joinToString(",") { "?" }
        execSQL("INSERT INTO $table ($names) VALUES ($placeholders)", rowValues.toTypedArray())
    }

    private fun databaseName(prefix: String): String = "${prefix}_${System.nanoTime()}.db"

    private data class Column(val name: String, val type: String, val notNull: Boolean)
}

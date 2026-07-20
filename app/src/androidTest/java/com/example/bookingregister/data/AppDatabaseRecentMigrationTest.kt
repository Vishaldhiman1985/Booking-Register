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
    fun version35MigratesTo39WithoutDataDestruction() {
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

        helper.runMigrationsAndValidate(name, 39, true, *AppDatabase.allMigrations()).use { db ->
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

        helper.runMigrationsAndValidate(name, 39, true, *AppDatabase.allMigrations()).use { db ->
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
    fun version38MigratesTo39WithoutSchemaChanges() {
        migrateEmptyDatabaseFrom(38)
    }

    private fun migrateEmptyDatabaseFrom(version: Int) {
        val name = databaseName("v$version")
        helper.createDatabase(name, version).close()
        helper.runMigrationsAndValidate(name, 39, true, *AppDatabase.allMigrations()).close()
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

    private fun databaseName(prefix: String): String = "${prefix}_${System.nanoTime()}.db"

    private data class Column(val name: String, val type: String, val notNull: Boolean)
}

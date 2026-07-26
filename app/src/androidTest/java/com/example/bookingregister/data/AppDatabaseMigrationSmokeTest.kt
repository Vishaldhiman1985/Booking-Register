package com.example.bookingregister.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationSmokeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun migratesBlankVersionOneDatabaseToCurrentSchema() {
        val dbName = nextDatabaseName("blank_v1")
        createLegacyDatabase(dbName, version = 1) { }

        val db = openWithProductionMigrations(dbName)
        try {
            assertNotNull(db.hotelDao())
            assertEquals(40, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationSeventeenToEighteenAddsPaymentAllocationColumns() {
        val dbName = nextDatabaseName("payment_v17")
        createLegacyDatabase(dbName, version = 17) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hotels (
                    remoteId TEXT NOT NULL PRIMARY KEY,
                    hotelName TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_payments (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    bookingRemoteId TEXT NOT NULL,
                    paymentType TEXT NOT NULL DEFAULT 'PAYMENT',
                    amount REAL NOT NULL DEFAULT 0.0,
                    paymentMillis INTEGER NOT NULL DEFAULT 0,
                    method TEXT,
                    note TEXT,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO hotels (remoteId, hotelName, updatedAt)
                VALUES ('hotel_1', 'Migration Hotel', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO booking_payments (
                    remoteId, hotelRemoteId, bookingRemoteId, paymentType,
                    amount, paymentMillis, updatedAt, syncState
                ) VALUES (
                    'payment_1', 'hotel_1', 'booking_1', 'PAYMENT',
                    1500.0, 1, 1, 'SYNCED'
                )
                """.trimIndent()
            )
        }

        val db = openWithProductionMigrations(dbName)
        try {
            val payment = runBlocking { db.bookingPaymentDao().getByRemoteId("payment_1") }
            assertNotNull(payment)
            assertEquals("STAY", payment?.paymentCategory)
            assertEquals(1500.0, payment?.allocatedStayAmount ?: 0.0, 0.001)
            assertEquals(40, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun openWithProductionMigrations(dbName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.allMigrations())
            .build()
            .also { it.openHelper.writableDatabase }
    }

    private fun createLegacyDatabase(
        dbName: String,
        version: Int,
        block: (SQLiteDatabase) -> Unit
    ) {
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            block(db)
            db.version = version
        }
    }

    private fun nextDatabaseName(prefix: String): String {
        val name = "${prefix}_${System.nanoTime()}.db"
        databaseNames += name
        return name
    }
}



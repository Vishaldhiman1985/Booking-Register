package com.example.bookingregister.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookingRoomNightAssignmentMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java
    )

    @Test
    fun migrate40To41_preservesExistingDataAndAddsRoomNightAssignments() {
        val databaseName = "booking-room-plan-migration-test.db"
        val context = instrumentation.targetContext

        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 40).apply {
            execSQL(
                """
                INSERT INTO hotels (
                    remoteId,
                    hotelName,
                    gstNumber,
                    address,
                    phone,
                    updatedAt,
                    isDeleted,
                    syncState,
                    lastSyncError,
                    lastSyncedAt,
                    revision,
                    baseRevision,
                    updatedByUid
                ) VALUES (
                    'hotel-migration-test',
                    'Migration Test Hotel',
                    NULL,
                    NULL,
                    NULL,
                    123456789,
                    0,
                    'SYNCED',
                    NULL,
                    NULL,
                    7,
                    7,
                    NULL
                )
                """.trimIndent()
            )
            close()
        }

        val migration40To41 = AppDatabase.allMigrations().single {
            it.startVersion == 40 && it.endVersion == 41
        }

        val migratedDatabase = migrationHelper.runMigrationsAndValidate(
            databaseName,
            41,
            true,
            migration40To41
        )

        migratedDatabase.query(
            """
            SELECT hotelName, revision
            FROM hotels
            WHERE remoteId = 'hotel-migration-test'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Migration Test Hotel", cursor.getString(0))
            assertEquals(7L, cursor.getLong(1))
        }

        migratedDatabase.query(
            "SELECT COUNT(*) FROM booking_room_night_assignments"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        migratedDatabase.close()
        context.deleteDatabase(databaseName)
    }
}
package com.example.bookingregister.finance.dev

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDemoDraftDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        FinanceDemoDraftDatabase::class.java
    )

    @Test
    fun migration1To2PreservesExistingDraftAndCreatesHistory() {
        val name = databaseName("migration")
        val context = instrumentation.targetContext
        context.deleteDatabase(name)
        try {
            migrationHelper.createDatabase(name, 1).apply {
                execSQL(
                    """INSERT INTO finance_demo_draft_bills (
                        draftId, environment, tenantId, hotelId, propertyId,
                        vendorCode, category, invoiceDate, accountingPeriod,
                        currencyCode, amountPaise, status, createdAtMillis
                    ) VALUES (
                        'legacy-draft', 'FINANCE_DEV', 'DEMO_TENANT_ONLY',
                        'DEMO_HOTEL_ONLY', 'DEMO_PROPERTY_ONLY', 'demo-rent',
                        'Lease / Rent', '2026-11-01', '2026-11', 'INR',
                        25000000, 'LOCAL_DRAFT_NOT_POSTED', 12345
                    )""".trimIndent()
                )
                close()
            }

            migrationHelper.runMigrationsAndValidate(
                name,
                2,
                true,
                FinanceDemoDraftDatabase.MIGRATION_1_2
            ).use { db ->
                db.query(
                    """SELECT amountPaise, paymentStatus, amountPaidPaise,
                              updatedAtMillis, revision, discardedAtMillis
                       FROM finance_demo_draft_bills WHERE draftId = 'legacy-draft'"""
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(25_000_000L, cursor.getLong(0))
                    assertEquals("NOT_PAID", cursor.getString(1))
                    assertEquals(0L, cursor.getLong(2))
                    assertEquals(12_345L, cursor.getLong(3))
                    assertEquals(1L, cursor.getLong(4))
                    assertTrue(cursor.isNull(5))
                }
                db.query(
                    """SELECT eventType, reason, resultingRevision
                       FROM finance_demo_draft_events WHERE draftId = 'legacy-draft'"""
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("MIGRATED", cursor.getString(0))
                    assertEquals("Preserved from Finance DEV schema v1", cursor.getString(1))
                    assertEquals(1L, cursor.getLong(2))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration4To5PreservesFinanceDataAndAddsRecurringBills() {
        val name = databaseName("recurring-migration")
        val context = instrumentation.targetContext
        context.deleteDatabase(name)
        try {
            migrationHelper.createDatabase(name, 4).apply {
                execSQL(
                    """INSERT INTO finance_demo_draft_bills (
                        draftId, environment, tenantId, hotelId, propertyId,
                        vendorCode, category, invoiceDate, accountingPeriod,
                        currencyCode, amountPaise, status, createdAtMillis,
                        paymentStatus, amountPaidPaise, updatedAtMillis,
                        revision, discardedAtMillis, department, notes
                    ) VALUES (
                        'before-recurring', 'FINANCE_DEV', 'DEMO_TENANT_ONLY',
                        'DEMO_HOTEL_ONLY', 'DEMO_PROPERTY_ONLY', 'demo-rent',
                        'Lease / Rent', '2026-11-01', '2026-11', 'INR',
                        5000000, 'LOCAL_DRAFT_NOT_POSTED', 12345,
                        'NOT_PAID', 0, 12345, 1, NULL, 'Rooms', 'Keep me'
                    )""".trimIndent()
                )
                close()
            }

            migrationHelper.runMigrationsAndValidate(
                name,
                5,
                true,
                FinanceDemoDraftDatabase.MIGRATION_4_5
            ).use { db ->
                db.query(
                    """SELECT amountPaise, department, notes
                       FROM finance_demo_draft_bills
                       WHERE draftId = 'before-recurring'"""
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(5_000_000L, cursor.getLong(0))
                    assertEquals("Rooms", cursor.getString(1))
                    assertEquals("Keep me", cursor.getString(2))
                }
                db.query(
                    """SELECT name FROM sqlite_master
                       WHERE type = 'table' AND name = 'finance_recurring_bills'"""
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun createEditDiscardPersistsAcrossDatabaseReopen() = runBlocking {
        val name = databaseName("persistence")
        val context = instrumentation.targetContext
        context.deleteDatabase(name)
        try {
            var database = open(name)
            val created = FinanceDemoDraftPolicy.create(
                input = FinanceDemoDraftPolicy.DraftInput(
                    vendorCode = "demo-maintenance",
                    category = "Maintenance",
                    invoiceDate = "2026-12-15",
                    amountPaise = 1_250_025L,
                    paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID,
                    amountPaidPaise = 250_010L
                ),
                id = "persisted-draft",
                nowMillis = 1_000L
            )
            database.drafts().createWithEvent(
                created,
                FinanceDemoDraftPolicy.createdEvent(created)
            )
            database.close()

            database = open(name)
            val reopened = database.drafts().find("persisted-draft")
            assertNotNull(reopened)
            assertEquals(1_250_025L, reopened?.amountPaise)
            assertEquals(250_010L, reopened?.amountPaidPaise)
            assertEquals(1, database.drafts().events("persisted-draft").size)

            val edited = FinanceDemoDraftPolicy.edit(
                original = requireNotNull(reopened),
                input = FinanceDemoDraftPolicy.DraftInput(
                    vendorCode = "demo-utilities",
                    category = "Utilities",
                    invoiceDate = "2027-01-02",
                    amountPaise = 2_000_000L,
                    paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID,
                    amountPaidPaise = 0L
                ),
                reason = "Correct fictional details",
                nowMillis = 2_000L
            )
            database.drafts().replaceWithEvent(
                edited,
                FinanceDemoDraftPolicy.editedEvent(edited, "Correct fictional details")
            )
            database.close()

            database = open(name)
            val editedAfterReopen = requireNotNull(database.drafts().find("persisted-draft"))
            assertEquals(2L, editedAfterReopen.revision)
            assertEquals("2027-01", editedAfterReopen.accountingPeriod)
            assertEquals(2, database.drafts().events("persisted-draft").size)

            val discarded = FinanceDemoDraftPolicy.discard(
                editedAfterReopen,
                "No longer needed",
                3_000L
            )
            database.drafts().replaceWithEvent(
                discarded,
                FinanceDemoDraftPolicy.discardedEvent(discarded, "No longer needed")
            )
            database.close()

            database = open(name)
            assertEquals(
                0L,
                database.drafts().activeCount(
                    FinanceDemoDraftPolicy.TENANT,
                    FinanceDemoDraftPolicy.PROPERTY,
                    FinanceDemoDraftPolicy.STATUS_ACTIVE
                )
            )
            assertEquals(
                1L,
                database.drafts().discardedCount(
                    FinanceDemoDraftPolicy.TENANT,
                    FinanceDemoDraftPolicy.PROPERTY,
                    FinanceDemoDraftPolicy.STATUS_DISCARDED
                )
            )
            assertEquals(3, database.drafts().events("persisted-draft").size)
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun recurringBillAppearsForPaymentOnlyOnItsDueDate() = runBlocking {
        val name = databaseName("recurring-due-date")
        val context = instrumentation.targetContext
        context.deleteDatabase(name)
        try {
            val database = open(name)
            val rule = FinanceRecurringPolicy.newRule(
                categoryCode = "HC01",
                categoryLabel = "Property",
                typeCode = "HE0101",
                typeLabel = "Lease",
                amountPaise = 5_000_000L,
                firstDueDate = "2027-01-02",
                endDate = null,
                nowMillis = 1_000L,
                id = "due-date-rule"
            )
            database.recurring().insertBills(
                FinanceRecurringPolicy.billsThrough(rule, LocalDate.parse("2027-02-02"), 1_000L)
            )

            assertTrue(
                database.recurring().openBills(
                    FinanceDemoDraftPolicy.TENANT,
                    FinanceDemoDraftPolicy.PROPERTY,
                    "2027-01-01"
                ).isEmpty()
            )
            assertEquals(
                listOf("2027-01-02"),
                database.recurring().openBills(
                    FinanceDemoDraftPolicy.TENANT,
                    FinanceDemoDraftPolicy.PROPERTY,
                    "2027-01-02"
                ).map { it.dueDate }
            )
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun open(name: String): FinanceDemoDraftDatabase =
        Room.databaseBuilder(
            instrumentation.targetContext,
            FinanceDemoDraftDatabase::class.java,
            name
        )
            .addMigrations(
                FinanceDemoDraftDatabase.MIGRATION_1_2,
                FinanceDemoDraftDatabase.MIGRATION_2_3,
                FinanceDemoDraftDatabase.MIGRATION_3_4,
                FinanceDemoDraftDatabase.MIGRATION_4_5
            )
            .build()

    private fun databaseName(prefix: String): String =
        "finance-dev-$prefix-${System.nanoTime()}.db"
}

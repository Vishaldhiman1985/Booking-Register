package com.example.bookingregister.finance.dev

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Private Finance DEV-only draft store. These are NOT posted journals, invoices, or liabilities. */
@Entity(
    tableName = "finance_demo_draft_bills",
    indices = [Index(value = ["tenantId", "propertyId", "createdAtMillis"])]
)
data class FinanceDemoDraftBill(
    @PrimaryKey val draftId: String,
    val environment: String,
    val tenantId: String,
    val hotelId: String,
    val propertyId: String,
    val vendorCode: String,
    val category: String,
    val invoiceDate: String,
    val accountingPeriod: String,
    val currencyCode: String,
    val amountPaise: Long,
    val status: String,
    val createdAtMillis: Long,
    @ColumnInfo(defaultValue = "'NOT_PAID'") val paymentStatus: String = "NOT_PAID",
    @ColumnInfo(defaultValue = "0") val amountPaidPaise: Long = 0L,
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = 0L,
    val discardedAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "1") val revision: Long = 1L
)

/** Append-only local history for draft edits and discards; it is not a financial audit ledger. */
@Entity(
    tableName = "finance_demo_draft_events",
    indices = [Index(value = ["draftId", "occurredAtMillis"])]
)
data class FinanceDemoDraftEvent(
    @PrimaryKey val eventId: String,
    val draftId: String,
    val eventType: String,
    val reason: String,
    val occurredAtMillis: Long,
    val resultingRevision: Long
)

@Dao
abstract class FinanceDemoDraftDao {
    @Query(
        """SELECT * FROM finance_demo_draft_bills
           WHERE tenantId = :tenantId AND propertyId = :propertyId AND status = :activeStatus
           ORDER BY updatedAtMillis DESC LIMIT 30"""
    )
    abstract suspend fun recentActive(
        tenantId: String,
        propertyId: String,
        activeStatus: String
    ): List<FinanceDemoDraftBill>

    @Query(
        """SELECT COUNT(*) FROM finance_demo_draft_bills
           WHERE tenantId = :tenantId AND propertyId = :propertyId AND status = :activeStatus"""
    )
    abstract suspend fun activeCount(
        tenantId: String,
        propertyId: String,
        activeStatus: String
    ): Long

    @Query(
        """SELECT COUNT(*) FROM finance_demo_draft_bills
           WHERE tenantId = :tenantId AND propertyId = :propertyId AND status = :discardedStatus"""
    )
    abstract suspend fun discardedCount(
        tenantId: String,
        propertyId: String,
        discardedStatus: String
    ): Long

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM finance_demo_draft_bills
           WHERE tenantId = :tenantId AND propertyId = :propertyId AND status = :activeStatus"""
    )
    abstract suspend fun totalActiveDraftPaise(
        tenantId: String,
        propertyId: String,
        activeStatus: String
    ): Long

    @Query("SELECT * FROM finance_demo_draft_bills WHERE draftId = :draftId LIMIT 1")
    abstract suspend fun find(draftId: String): FinanceDemoDraftBill?

    @Query(
        """SELECT * FROM finance_demo_draft_events
           WHERE draftId = :draftId ORDER BY occurredAtMillis ASC, eventId ASC"""
    )
    abstract suspend fun events(draftId: String): List<FinanceDemoDraftEvent>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRow(row: FinanceDemoDraftBill)

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateRow(row: FinanceDemoDraftBill): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEvent(event: FinanceDemoDraftEvent)

    @Transaction
    open suspend fun createWithEvent(row: FinanceDemoDraftBill, event: FinanceDemoDraftEvent) {
        require(event.draftId == row.draftId && event.resultingRevision == row.revision) {
            "Draft creation event does not match the draft"
        }
        insertRow(row)
        insertEvent(event)
    }

    @Transaction
    open suspend fun replaceWithEvent(row: FinanceDemoDraftBill, event: FinanceDemoDraftEvent) {
        require(event.draftId == row.draftId && event.resultingRevision == row.revision) {
            "Draft change event does not match the draft"
        }
        check(updateRow(row) == 1) { "Draft no longer exists" }
        insertEvent(event)
    }
}

/** Version 2 evolves the isolated synthetic store without granting posting authority. */
@Database(
    entities = [FinanceDemoDraftBill::class, FinanceDemoDraftEvent::class],
    version = 2,
    exportSchema = true
)
abstract class FinanceDemoDraftDatabase : RoomDatabase() {
    abstract fun drafts(): FinanceDemoDraftDao

    companion object {
        // Keep the original filename so Phase 2A device drafts are migrated rather than abandoned.
        const val DATABASE_NAME = "finance_dev_synthetic_drafts_v1.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE finance_demo_draft_bills " +
                        "ADD COLUMN paymentStatus TEXT NOT NULL DEFAULT 'NOT_PAID'"
                )
                db.execSQL(
                    "ALTER TABLE finance_demo_draft_bills " +
                        "ADD COLUMN amountPaidPaise INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE finance_demo_draft_bills " +
                        "ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE finance_demo_draft_bills " +
                        "ADD COLUMN discardedAtMillis INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE finance_demo_draft_bills " +
                        "ADD COLUMN revision INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "UPDATE finance_demo_draft_bills SET updatedAtMillis = createdAtMillis " +
                        "WHERE updatedAtMillis = 0"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS finance_demo_draft_events (
                        eventId TEXT NOT NULL,
                        draftId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        occurredAtMillis INTEGER NOT NULL,
                        resultingRevision INTEGER NOT NULL,
                        PRIMARY KEY(eventId)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS index_finance_demo_draft_events_draftId_occurredAtMillis
                       ON finance_demo_draft_events (draftId, occurredAtMillis)""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO finance_demo_draft_events (
                        eventId, draftId, eventType, reason, occurredAtMillis, resultingRevision
                    )
                    SELECT 'migration-v2-' || draftId, draftId, 'MIGRATED',
                           'Preserved from Finance DEV schema v1', createdAtMillis, 1
                    FROM finance_demo_draft_bills""".trimIndent()
                )
            }
        }

        fun build(context: Context): FinanceDemoDraftDatabase =
            Room.databaseBuilder(context, FinanceDemoDraftDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

package com.example.bookingregister.finance.dev

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

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
    val createdAtMillis: Long
)

@Dao
interface FinanceDemoDraftDao {
    @Query("SELECT * FROM finance_demo_draft_bills WHERE tenantId = :tenantId AND propertyId = :propertyId ORDER BY createdAtMillis DESC LIMIT 30")
    suspend fun recent(tenantId: String, propertyId: String): List<FinanceDemoDraftBill>

    @Query("SELECT COUNT(*) FROM finance_demo_draft_bills WHERE tenantId = :tenantId AND propertyId = :propertyId")
    suspend fun count(tenantId: String, propertyId: String): Long

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM finance_demo_draft_bills WHERE tenantId = :tenantId AND propertyId = :propertyId")
    suspend fun totalDraftPaise(tenantId: String, propertyId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: FinanceDemoDraftBill)
}

/** Version 1 is an isolated, synthetic-only preview database, not a production Finance schema. */
@Database(entities = [FinanceDemoDraftBill::class], version = 1, exportSchema = true)
abstract class FinanceDemoDraftDatabase : RoomDatabase() {
    abstract fun drafts(): FinanceDemoDraftDao
}

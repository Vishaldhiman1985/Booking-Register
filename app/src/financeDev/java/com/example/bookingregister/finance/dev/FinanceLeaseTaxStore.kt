package com.example.bookingregister.finance.dev

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Sidecar obligation tracker attached to ORIGINAL fixed-rent rule IDs.
 * Tax amounts are NOT expenses, payments are NOT bank transfers, and this is NOT a GST filing ledger.
 * Existing fixed-expense schema and its payments are unchanged.
 */
internal class FinanceLeaseTaxStore(context: Context) :
    SQLiteOpenHelper(context, "finance_dev_lease_tax_v19.db", null, 1) {
    private val entryLock = FinancePeriodEntryLock(context.applicationContext)

    data class Declaration(
        val ruleId: String, val property: String, val month: String,
        val landlordStatus: String, val gstin: String,
        val mode: String, val rateBps: Int, val reason: String
    )
    data class TaxBill(
        val id: String, val ruleId: String, val month: String, val rentBillId: String,
        val rentPaise: Long, val mode: String, val taxPaise: Long, val paidPaise: Long
    ) {
        val remainingPaise: Long get() = taxPaise - paidPaise
    }
    data class Audit(val action: String, val description: String, val timestamp: Long)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE lease_tax_declarations (
            rule_id TEXT NOT NULL, effective_month TEXT NOT NULL,
            property_name TEXT NOT NULL, landlord_status TEXT NOT NULL, landlord_gstin TEXT NOT NULL,
            tax_mode TEXT NOT NULL, rate_bps INTEGER NOT NULL, reason TEXT NOT NULL,
            created_at INTEGER NOT NULL, PRIMARY KEY(rule_id,effective_month)
        )""")
        db.execSQL("""CREATE TABLE lease_tax_bills (
            id TEXT PRIMARY KEY, rule_id TEXT NOT NULL, month TEXT NOT NULL,
            rent_bill_id TEXT NOT NULL UNIQUE, rent_paise INTEGER NOT NULL CHECK(rent_paise >= 0),
            tax_mode TEXT NOT NULL, tax_paise INTEGER NOT NULL CHECK(tax_paise >= 0),
            created_at INTEGER NOT NULL, UNIQUE(rule_id,month)
        )""")
        db.execSQL("""CREATE TABLE lease_tax_payments (
            id TEXT PRIMARY KEY, tax_bill_id TEXT NOT NULL REFERENCES lease_tax_bills(id),
            paid_on TEXT NOT NULL, amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
            method TEXT NOT NULL, reference TEXT NOT NULL, created_at INTEGER NOT NULL,
            UNIQUE(tax_bill_id,method,reference)
        )""")
        db.execSQL("""CREATE TABLE lease_tax_audit (
            id TEXT PRIMARY KEY, reference_id TEXT NOT NULL, action TEXT NOT NULL,
            description TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX lease_tax_payment_bill_idx ON lease_tax_payments(tax_bill_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Unsupported lease tax migration $oldVersion → $newVersion; existing data preserved")
    }
    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val value = block(db)
            db.setTransactionSuccessful()
            return value
        } finally { db.endTransaction() }
    }
    private inline fun <T> query(db: SQLiteDatabase, sql: String, args: Array<String> = emptyArray(),
                                 map: (Cursor) -> T): List<T> = db.rawQuery(sql, args).use { c ->
        buildList { while (c.moveToNext()) add(map(c)) }
    }
    private fun audit(db: SQLiteDatabase, id: String, event: String, message: String) {
        db.insertOrThrow("lease_tax_audit", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("reference_id", id)
            put("action", event); put("description", message)
            put("created_at", System.currentTimeMillis())
        })
    }

    private fun declaration(c: Cursor) = Declaration(
        c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
        c.getString(5), c.getInt(6), c.getString(7)
    )
    fun declarations(ruleId: String): List<Declaration> = query(readableDatabase, """
        SELECT rule_id,property_name,effective_month,landlord_status,landlord_gstin,tax_mode,rate_bps,reason
        FROM lease_tax_declarations WHERE rule_id=? ORDER BY effective_month ASC
    """.trimIndent(), arrayOf(ruleId), ::declaration)
    fun trackedRuleIds(): Set<String> = query(readableDatabase,
        "SELECT DISTINCT rule_id FROM lease_tax_declarations", map = { it.getString(0) }).toSet()
    fun latestDeclaration(ruleId: String): Declaration? = declarations(ruleId).lastOrNull()
    fun latestIssuedMonth(ruleId: String): YearMonth? = query(readableDatabase,
        "SELECT MAX(month) FROM lease_tax_bills WHERE rule_id=?", arrayOf(ruleId)) {
        if (it.isNull(0)) null else YearMonth.parse(it.getString(0))
    }.singleOrNull()

    fun declare(ruleId: String, property: String, landlordStatus: String, gstin: String,
                mode: String, rateBps: Int, effectiveMonth: String, reason: String) {
        require(ruleId.isNotBlank())
        require(property.trim().isNotBlank()) { "Enter the property name" }
        require(reason.trim().length >= 3) { "Provide a reason for the declaration" }
        FinanceLeaseTaxPolicy.validateDeclaration(landlordStatus, gstin, mode, rateBps, effectiveMonth)
        val period = YearMonth.parse(effectiveMonth)
        val latest = latestIssuedMonth(ruleId)
        require(latest == null || period > latest) { "Already issued months cannot be changed; choose a later month" }
        val existing = declarations(ruleId)
        require(existing.none { it.month == effectiveMonth }) { "Declaration already exists for this month" }
        require(existing.isEmpty() || period > YearMonth.parse(existing.last().month)) {
            "Declaration changes must be effective after the last declaration"
        }
        tx { db ->
            db.insertOrThrow("lease_tax_declarations", null, ContentValues().apply {
                put("rule_id", ruleId); put("effective_month", effectiveMonth)
                put("property_name", property.trim()); put("landlord_status", landlordStatus)
                put("landlord_gstin", gstin.trim().uppercase()); put("tax_mode", mode)
                put("rate_bps", rateBps); put("reason", reason.trim())
                put("created_at", System.currentTimeMillis())
            })
            audit(db, ruleId, "DECLARATION", "$effectiveMonth · $mode · $rateBps bps · ${reason.trim()}")
        }
    }

    /** Generates a single tax observation for each issued fixed-rent bill; idempotent across retries. */
    fun issueForBills(rentBills: List<FinanceFixedExpenseStore.Bill>): Int {
        val ids = trackedRuleIds()
        if (ids.isEmpty()) return 0
        val declared = ids.associateWith(::declarations)
        var insertedCount = 0
        tx { db ->
            rentBills.forEach { bill ->
                if (bill.ruleId !in ids) return@forEach
                val current = declared[bill.ruleId].orEmpty()
                    .lastOrNull { YearMonth.parse(it.month) <= YearMonth.parse(bill.month) }
                    ?: return@forEach
                val value = FinanceLeaseTaxPolicy.split(bill.amount, current.mode, current.rateBps)
                val taxId = "lease-tax:${bill.ruleId}:${bill.month}"
                val result = db.insertWithOnConflict("lease_tax_bills", null, ContentValues().apply {
                    put("id", taxId); put("rule_id", bill.ruleId); put("month", bill.month)
                    put("rent_bill_id", bill.id); put("rent_paise", bill.amount)
                    put("tax_mode", current.mode); put("tax_paise", value.taxPaise)
                    put("created_at", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_IGNORE)
                if (result != -1L) {
                    insertedCount++
                    audit(db, taxId, "ISSUED", "${bill.month} · ${current.mode} · ${value.taxPaise} paise")
                }
            }
        }
        return insertedCount
    }

    fun bills(ruleId: String? = null): List<TaxBill> {
        val sql = """SELECT b.id,b.rule_id,b.month,b.rent_bill_id,b.rent_paise,b.tax_mode,b.tax_paise,
                 COALESCE((SELECT SUM(p.amount_paise) FROM lease_tax_payments p WHERE p.tax_bill_id=b.id),0)
                 FROM lease_tax_bills b""" + (if (ruleId != null) " WHERE b.rule_id=?" else "") + " ORDER BY b.month DESC,b.id"
        return query(readableDatabase, sql, if (ruleId == null) emptyArray() else arrayOf(ruleId)) { c ->
            TaxBill(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getLong(4), c.getString(5), c.getLong(6), c.getLong(7))
        }
    }

    /** A recorded transfer is evidence supplied by the user, not an automatic government filing. */
    fun recordPayment(taxBillId: String, amountPaise: Long, paidOn: String,
                      method: String, reference: String) = tx { db ->
        val day = LocalDate.parse(paidOn)
        entryLock.requireDateOpen(paidOn)
        require(!day.isAfter(LocalDate.now())) { "Future transfers cannot be recorded as paid" }
        require(amountPaise > 0) { "Enter a positive amount" }
        require(method.trim().isNotEmpty() && reference.trim().length >= 3) {
            "Payment method and reference/remark are required"
        }
        val found = query(db, """SELECT b.tax_paise,
             COALESCE((SELECT SUM(p.amount_paise) FROM lease_tax_payments p WHERE p.tax_bill_id=b.id),0)
             FROM lease_tax_bills b WHERE b.id=?""", arrayOf(taxBillId)) { c -> c.getLong(0) to c.getLong(1) }
            .singleOrNull() ?: error("Tax obligation not found")
        val remaining = found.first - found.second
        require(remaining > 0 && amountPaise <= remaining) { "Amount exceeds the remaining GST/RCM due" }
        db.insertOrThrow("lease_tax_payments", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("tax_bill_id", taxBillId)
            put("paid_on", day.toString()); put("amount_paise", amountPaise)
            put("method", method.trim()); put("reference", reference.trim())
            put("created_at", System.currentTimeMillis())
        })
        audit(db, taxBillId, "PAYMENT_RECORDED", "$paidOn · $amountPaise paise · $method · ${reference.trim()}")
    }
    fun history(ruleId: String): List<Audit> = query(readableDatabase, """
        SELECT action,description,created_at FROM lease_tax_audit
        WHERE reference_id=? OR reference_id IN (SELECT id FROM lease_tax_bills WHERE rule_id=?)
        ORDER BY created_at DESC LIMIT 150
    """.trimIndent(), arrayOf(ruleId, ruleId)) { c ->
        Audit(c.getString(0), c.getString(1), c.getLong(2))
    }
}

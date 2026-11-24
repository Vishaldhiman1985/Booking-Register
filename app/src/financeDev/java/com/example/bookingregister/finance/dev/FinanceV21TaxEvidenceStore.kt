package com.example.bookingregister.finance.dev

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.bookingregister.finance.domain.FinanceV21TaxEvidencePolicy
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Local Finance DEV sidecar for USER-DECLARED supporting tax evidence.
 * This is not a statutory ledger, tax determination or bank reconciliation.
 * Rent GST/RCM already appears in FinanceLeaseTaxStore and is EXCLUDED here.
 */
internal class FinanceV21TaxEvidenceStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "finance_dev_v21_tax_evidence.db", null, 1) {
    private val appContext = context.applicationContext
    private val policy = FinanceV21TaxEvidencePolicy

    data class Entry(
        val id: String, val kind: FinanceV21TaxEvidencePolicy.Kind,
        val property: String, val period: String, val documentDate: String,
        val documentRef: String, val party: String, val amountPaise: Long,
        val gstin: String, val section: String, val note: String,
        val settledPaise: Long, val cancelled: Boolean
    ) {
        val openPaise: Long get() = FinanceV21TaxEvidencePolicy.remaining(kind, amountPaise, settledPaise)
    }
    data class Settlement(val id: String, val entryId: String, val date: String, val amountPaise: Long,
                          val method: String, val reference: String, val voided: Boolean)
    data class Audit(val action: String, val detail: String, val occurredAt: Long)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tax_evidence (
            id TEXT PRIMARY KEY, kind TEXT NOT NULL, property TEXT NOT NULL,
            period TEXT NOT NULL, document_date TEXT NOT NULL, document_ref TEXT NOT NULL,
            party TEXT NOT NULL, amount_paise INTEGER NOT NULL CHECK(amount_paise>0),
            gstin TEXT NOT NULL, tax_section TEXT NOT NULL, note TEXT NOT NULL,
            cancelled INTEGER NOT NULL DEFAULT 0 CHECK(cancelled IN (0,1)),
            created_at INTEGER NOT NULL,
            UNIQUE(property, period, kind, document_ref)
        )""")
        db.execSQL("""CREATE TABLE tax_evidence_settlements (
            id TEXT PRIMARY KEY, entry_id TEXT NOT NULL REFERENCES tax_evidence(id),
            paid_on TEXT NOT NULL, amount_paise INTEGER NOT NULL CHECK(amount_paise>0),
            method TEXT NOT NULL, reference TEXT NOT NULL, voided INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL, UNIQUE(entry_id, method, reference)
        )""")
        db.execSQL("""CREATE TABLE tax_evidence_audit (
            id TEXT PRIMARY KEY, entry_id TEXT NOT NULL REFERENCES tax_evidence(id),
            action TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX tax_evidence_period ON tax_evidence(period,property)")
        db.execSQL("CREATE INDEX tax_evidence_settlement_entry ON tax_evidence_settlements(entry_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported V21 tax evidence migration $oldVersion -> $newVersion; existing data preserved")
    }
    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
    private inline fun <T> query(db: SQLiteDatabase, sql: String, args: Array<String> = emptyArray(),
                                 transform: (Cursor) -> T): List<T> = db.rawQuery(sql, args).use { cursor ->
        buildList { while (cursor.moveToNext()) add(transform(cursor)) }
    }
    private fun audit(db: SQLiteDatabase, entryId: String, action: String, detail: String) {
        db.insertOrThrow("tax_evidence_audit", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("entry_id", entryId)
            put("action", action); put("detail", detail); put("created_at", System.currentTimeMillis())
        })
    }
    private val selection = """e.id,e.kind,e.property,e.period,e.document_date,e.document_ref,e.party,
            e.amount_paise,e.gstin,e.tax_section,e.note,
            COALESCE((SELECT SUM(s.amount_paise) FROM tax_evidence_settlements s
            WHERE s.entry_id=e.id AND s.voided=0),0),e.cancelled"""
    private fun map(c: Cursor) = Entry(
        c.getString(0), FinanceV21TaxEvidencePolicy.Kind.valueOf(c.getString(1)),
        c.getString(2), c.getString(3), c.getString(4), c.getString(5),
        c.getString(6), c.getLong(7), c.getString(8), c.getString(9),
        c.getString(10), c.getLong(11), c.getInt(12) != 0
    )
    fun entries(period: String): List<Entry> {
        YearMonth.parse(period)
        return query(readableDatabase, "SELECT $selection FROM tax_evidence e WHERE e.period=? ORDER BY e.created_at DESC",
            arrayOf(period), ::map)
    }
    private fun get(db: SQLiteDatabase, id: String) = query(db,
        "SELECT $selection FROM tax_evidence e WHERE e.id=?", arrayOf(id), ::map).singleOrNull()

    fun add(input: FinanceV21TaxEvidencePolicy.Input): String {
        val period = policy.validate(input)
        FinancePeriodEntryLock(appContext).requireDateOpen(input.date)
        val id = UUID.randomUUID().toString()
        tx { db ->
            db.insertOrThrow("tax_evidence", null, ContentValues().apply {
                put("id", id); put("kind", input.kind.name)
                put("property", input.property.trim()); put("period", period)
                put("document_date", input.date); put("document_ref", input.documentRef.trim())
                put("party", input.party.trim()); put("amount_paise", input.amountPaise)
                put("gstin", input.gstin.trim().uppercase()); put("tax_section", input.section.trim())
                put("note", input.note.trim()); put("created_at", System.currentTimeMillis())
            })
            audit(db, id, "EVIDENCE_ADDED", "${input.kind.name} | ${input.documentRef.trim()} | ${input.amountPaise} paise")
        }
        return id
    }

    fun settle(id: String, amountPaise: Long, date: String, method: String, reference: String): String {
        val day = LocalDate.parse(date)
        require(!day.isAfter(LocalDate.now())) { "Future payments cannot be marked as made" }
        FinancePeriodEntryLock(appContext).requireDateOpen(date)
        require(method.trim().length in 2..40 && reference.trim().length in 3..100) {
            "Payment method and reference are required"
        }
        val settlementId = UUID.randomUUID().toString()
        tx { db ->
            val evidence = get(db,id) ?: error("Tax evidence not found")
            require(!evidence.cancelled) { "Cannot settle cancelled evidence" }
            policy.requireSettlement(evidence.kind, evidence.amountPaise, evidence.settledPaise, amountPaise)
            db.insertOrThrow("tax_evidence_settlements", null, ContentValues().apply {
                put("id", settlementId); put("entry_id", id); put("paid_on", date)
                put("amount_paise", amountPaise); put("method", method.trim()); put("reference", reference.trim())
                put("created_at", System.currentTimeMillis())
            })
            audit(db,id,"SETTLEMENT_RECORDED","$date | $amountPaise paise | ${method.trim()} | ${reference.trim()}")
        }
        return settlementId
    }

    fun settlements(entryId: String): List<Settlement> = query(readableDatabase,
        """SELECT id,entry_id,paid_on,amount_paise,method,reference,voided
           FROM tax_evidence_settlements WHERE entry_id=? ORDER BY created_at DESC""",
        arrayOf(entryId)) { c -> Settlement(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),
            c.getString(4),c.getString(5),c.getInt(6)!=0) }

    fun voidSettlement(id: String, reason: String) {
        policy.requireCorrectionReason(reason)
        tx { db ->
            val current = query(db, "SELECT entry_id,paid_on,voided FROM tax_evidence_settlements WHERE id=?",
                arrayOf(id)) { c -> Triple(c.getString(0), c.getString(1), c.getInt(2) != 0) }.singleOrNull()
                ?: error("Settlement not found")
            require(!current.third) { "Settlement already voided" }
            FinancePeriodEntryLock(appContext).requireDateOpen(current.second)
            val values = ContentValues().apply { put("voided",1) }
            check(db.update("tax_evidence_settlements", values, "id=? AND voided=0", arrayOf(id))==1)
            audit(db,current.first,"SETTLEMENT_VOIDED","Settlement $id | ${reason.trim()}")
        }
    }

    fun cancel(id: String, reason: String) {
        policy.requireCorrectionReason(reason)
        tx { db ->
            val entry = get(db,id) ?: error("Tax evidence not found")
            require(!entry.cancelled) { "Evidence already cancelled" }
            FinancePeriodEntryLock(appContext).requireDateOpen(entry.documentDate)
            require(entry.settledPaise == 0L) { "Void recorded settlements before cancelling evidence" }
            val values = ContentValues().apply { put("cancelled",1) }
            check(db.update("tax_evidence", values, "id=? AND cancelled=0", arrayOf(id))==1)
            audit(db,id,"EVIDENCE_CANCELLED",reason.trim())
        }
    }
    fun history(entryId: String): List<Audit> = query(readableDatabase,
        "SELECT action,detail,created_at FROM tax_evidence_audit WHERE entry_id=? ORDER BY created_at DESC",
        arrayOf(entryId)) { c -> Audit(c.getString(0),c.getString(1),c.getLong(2)) }
}

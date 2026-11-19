package com.example.bookingregister.finance.dev

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Isolated on-device DEV ledger. Not a production journal, tax engine or bank payment. */
internal class FinanceFixedExpenseStore(context: Context) : SQLiteOpenHelper(context, "finance_dev_fixed_expenses_v16.db", null, 1) {
    data class Rule(
        val id: String, val categoryCode: String, val category: String, val typeCode: String,
        val type: String, val vendorId: String, val vendor: String, val department: String,
        val dueDay: Int, val startMonth: String, val endMonth: String?, val active: Boolean
    )
    data class Bill(
        val id: String, val ruleId: String, val type: String, val vendor: String,
        val department: String, val month: String, val dueDate: String,
        val amount: Long, val paid: Long
    ) {
        val remaining: Long get() = amount - paid
    }
    data class Payment(val id: String, val billId: String, val date: String, val amount: Long, val method: String, val reference: String)
    data class Event(val action: String, val detail: String, val at: Long)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE fixed_rules (
            id TEXT PRIMARY KEY, category_code TEXT NOT NULL, category TEXT NOT NULL,
            type_code TEXT NOT NULL, type TEXT NOT NULL, vendor_id TEXT NOT NULL, vendor TEXT NOT NULL,
            department TEXT NOT NULL, due_day INTEGER NOT NULL, start_month TEXT NOT NULL,
            end_month TEXT, active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE fixed_revisions (
            id TEXT PRIMARY KEY, rule_id TEXT NOT NULL REFERENCES fixed_rules(id),
            effective_month TEXT NOT NULL, amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
            reason TEXT NOT NULL, created_at INTEGER NOT NULL,
            UNIQUE(rule_id, effective_month)
        )""")
        db.execSQL("""CREATE TABLE fixed_bills (
            id TEXT PRIMARY KEY, rule_id TEXT NOT NULL REFERENCES fixed_rules(id),
            month TEXT NOT NULL, due_date TEXT NOT NULL, amount_paise INTEGER NOT NULL,
            created_at INTEGER NOT NULL, UNIQUE(rule_id,month)
        )""")
        db.execSQL("""CREATE TABLE fixed_payments (
            id TEXT PRIMARY KEY, bill_id TEXT NOT NULL REFERENCES fixed_bills(id),
            paid_on TEXT NOT NULL, amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
            method TEXT NOT NULL, reference TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE fixed_events (
            id TEXT PRIMARY KEY, reference_id TEXT NOT NULL, action TEXT NOT NULL,
            detail TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX fixed_bill_month_idx ON fixed_bills(month, due_date)")
        db.execSQL("CREATE INDEX fixed_payment_bill_idx ON fixed_payments(bill_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Unsupported fixed-expense database migration $oldVersion to $newVersion. Data was not deleted.")
    }
    private fun audit(db: SQLiteDatabase, ref: String, action: String, detail: String) {
        db.insertOrThrow("fixed_events", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("reference_id", ref)
            put("action", action); put("detail", detail); put("created_at", System.currentTimeMillis())
        })
    }
    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { val value = block(db); db.setTransactionSuccessful(); return value }
        finally { db.endTransaction() }
    }
    private inline fun <T> select(db: SQLiteDatabase, sql: String, args: Array<String> = emptyArray(), convert: (Cursor) -> T): List<T> {
        return db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(convert(c)) } }
    }
    private fun rule(c: Cursor) = Rule(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getInt(8),c.getString(9),if(c.isNull(10))null else c.getString(10),c.getInt(11)==1)
    fun rules(): List<Rule> = select(readableDatabase,
        "SELECT id,category_code,category,type_code,type,vendor_id,vendor,department,due_day,start_month,end_month,active FROM fixed_rules ORDER BY active DESC, created_at DESC", convert=::rule)
    fun create(categoryCode: String, category: String, typeCode: String, type: String, vendorId: String, vendor: String, department: String, dueDay: Int, startMonth: String, amountPaise: Long): String {
        require(categoryCode.isNotBlank() && typeCode.isNotBlank() && vendor.isNotBlank() && department.isNotBlank())
        require(categoryCode != FinanceExpenseCategoryRules.STAFF_ONLY) {
            "Staff & Employee Benefits must be recorded in Manpower, not recurring expenses"
        }
        require(amountPaise > 0 && dueDay in 1..31)
        FinanceFixedPolicy.month(startMonth)
        val id = UUID.randomUUID().toString()
        tx { db ->
            db.insertOrThrow("fixed_rules",null,ContentValues().apply {
                put("id",id);put("category_code",categoryCode);put("category",category)
                put("type_code",typeCode);put("type",type);put("vendor_id",vendorId);put("vendor",vendor)
                put("department",department);put("due_day",dueDay);put("start_month",startMonth)
                put("active",1);put("created_at",System.currentTimeMillis())
            })
            db.insertOrThrow("fixed_revisions",null,ContentValues().apply {
                put("id",UUID.randomUUID().toString());put("rule_id",id)
                put("effective_month",startMonth);put("amount_paise",amountPaise)
                put("reason","Initial recurring agreement");put("created_at",System.currentTimeMillis())
            })
            audit(db,id,"CREATE","$type · $startMonth · ${FinanceFixedPolicy.rupees(amountPaise)}")
        }
        return id
    }
    fun latestBillMonth(id: String): YearMonth? = select(readableDatabase,
        "SELECT MAX(month) FROM fixed_bills WHERE rule_id=?", arrayOf(id)) { c -> if(c.isNull(0))null else YearMonth.parse(c.getString(0)) }.firstOrNull()
    fun revisions(id: String): List<Pair<YearMonth,Long>> = select(readableDatabase,
        "SELECT effective_month,amount_paise FROM fixed_revisions WHERE rule_id=? ORDER BY effective_month",arrayOf(id)) {
        c -> YearMonth.parse(c.getString(0)) to c.getLong(1)
    }
    fun revise(rule: Rule, newAmount: Long, effectiveMonth: String, reason: String) {
        require(rule.active) { "Rule is stopped" }
        require(newAmount>0 && reason.trim().length>=3) { "New amount and a reason are required" }
        val effective=FinanceFixedPolicy.month(effectiveMonth)
        FinanceFixedPolicy.validateRevision(effective,YearMonth.parse(rule.startMonth),latestBillMonth(rule.id))
        require(rule.endMonth==null || effective <= YearMonth.parse(rule.endMonth))
        tx { db ->
            db.insertOrThrow("fixed_revisions",null,ContentValues().apply {
                put("id",UUID.randomUUID().toString());put("rule_id",rule.id)
                put("effective_month",effectiveMonth);put("amount_paise",newAmount)
                put("reason",reason.trim());put("created_at",System.currentTimeMillis())
            })
            audit(db,rule.id,"REVISE","From $effectiveMonth → ${FinanceFixedPolicy.rupees(newAmount)} · ${reason.trim()}")
        }
    }
    fun stop(rule: Rule, stopFromMonth: String, reason: String) {
        require(rule.active && reason.trim().length>=3)
        val cutoff=FinanceFixedPolicy.month(stopFromMonth)
        require(cutoff > YearMonth.parse(rule.startMonth)) { "Stop month must be after start month" }
        require(latestBillMonth(rule.id)?.let { cutoff > it } ?: true) { "Already generated bills cannot be removed" }
        tx { db ->
            val n=db.update("fixed_rules",ContentValues().apply { put("end_month",cutoff.minusMonths(1).toString());put("active",0) },"id=? AND active=1",arrayOf(rule.id))
            require(n==1) { "Rule has already changed" }
            audit(db,rule.id,"STOP","No new bills from $stopFromMonth · ${reason.trim()}")
        }
    }
    fun issueThrough(through: YearMonth): Int {
        val start=YearMonth.now().minusYears(12)
        val target=minOf(through,YearMonth.now())
        var created=0
        tx { db ->
            val all=select(db,"SELECT id,category_code,category,type_code,type,vendor_id,vendor,department,due_day,start_month,end_month,active FROM fixed_rules",convert=::rule)
            for(r in all) {
                var month=maxOf(YearMonth.parse(r.startMonth),start)
                val end=r.endMonth?.let(YearMonth::parse)
                val rates=select(db,"SELECT effective_month,amount_paise FROM fixed_revisions WHERE rule_id=?",arrayOf(r.id)) { c -> YearMonth.parse(c.getString(0)) to c.getLong(1) }
                while(month<=target && (end==null || month<=end)) {
                    val amount=FinanceFixedPolicy.effectiveAmount(month,rates)
                    val id="fixed:${r.id}:$month"
                    val inserted=db.insertWithOnConflict("fixed_bills",null,ContentValues().apply {
                        put("id",id);put("rule_id",r.id);put("month",month.toString())
                        put("due_date",FinanceFixedPolicy.due(month,r.dueDay).toString())
                        put("amount_paise",amount);put("created_at",System.currentTimeMillis())
                    },SQLiteDatabase.CONFLICT_IGNORE)
                    if(inserted != -1L) { created++;audit(db,id,"BILL_ISSUED","${r.type} $month · ${FinanceFixedPolicy.rupees(amount)}") }
                    month=month.plusMonths(1)
                }
            }
        }
        return created
    }
    fun bills(): List<Bill> = select(readableDatabase,"""
        SELECT b.id,b.rule_id,r.type,r.vendor,r.department,b.month,b.due_date,b.amount_paise,
        COALESCE((SELECT SUM(amount_paise) FROM fixed_payments p WHERE p.bill_id=b.id),0)
        FROM fixed_bills b JOIN fixed_rules r ON r.id=b.rule_id
        ORDER BY b.due_date DESC,b.id
    """.trimIndent()) { c -> Bill(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),c.getLong(8)) }
    fun payments(billId:String):List<Payment> = select(readableDatabase,"SELECT id,bill_id,paid_on,amount_paise,method,reference FROM fixed_payments WHERE bill_id=? ORDER BY paid_on",arrayOf(billId)) { c -> Payment(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getString(5)) }
    fun pay(billId:String,amount:Long,date:String,method:String,reference:String):String {
        require(amount>0 && method.trim().isNotBlank())
        LocalDate.parse(date)
        val id=UUID.randomUUID().toString()
        tx { db ->
            val rows=select(db,"""SELECT b.amount_paise,COALESCE((SELECT SUM(p.amount_paise) FROM fixed_payments p WHERE p.bill_id=b.id),0) FROM fixed_bills b WHERE b.id=?""",arrayOf(billId)){c->c.getLong(0) to c.getLong(1)}
            require(rows.size==1 && amount<=FinanceFixedPolicy.remaining(rows[0].first,rows[0].second)) { "Amount exceeds outstanding balance" }
            db.insertOrThrow("fixed_payments",null,ContentValues().apply {
                put("id",id);put("bill_id",billId);put("paid_on",date);put("amount_paise",amount)
                put("method",method.trim());put("reference",reference.trim());put("created_at",System.currentTimeMillis())
            })
            audit(db,billId,"PAYMENT","${FinanceFixedPolicy.rupees(amount)} · $date · $method · $reference")
        }
        return id
    }
    fun history():List<Event> = select(readableDatabase,"SELECT action,detail,created_at FROM fixed_events ORDER BY created_at DESC LIMIT 100") { c -> Event(c.getString(0),c.getString(1),c.getLong(2)) }
}

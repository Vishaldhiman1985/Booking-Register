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
 * V17 isolated local DEV payable engine. One authoritative local balance projection for the
 * Finance DEV screens; NOT a production journal, synchronized service or banking integration.
 * Existing V16 sources remain untouched. Their historical records are imported idempotently.
 */
internal class FinanceActivityStore(context: Context) : SQLiteOpenHelper(context, "finance_dev_activity_v17.db", null, 2) {
    private val contextForStore:Context = context.applicationContext
    data class Bill(
        val id: String, val source: String, val title: String, val vendorId: String,
        val vendor: String, val department: String, val date: String, val dueDate: String,
        val period: String, val amount: Long, val openingPaid: Long, val allocated: Long,
        val stockPending: Boolean
    ) {
        val paid: Long get() = Math.addExact(openingPaid,allocated)
        val due: Long get() = FinanceActivityPolicy.due(amount,openingPaid,allocated)
        val status: String get() = FinanceActivityPolicy.status(amount,due)
    }
    data class Payment(
        val id:String, val vendorId:String, val vendor:String, val date:String, val amount:Long,
        val method:String, val reference:String, val kind:String, val allocated:Long
    ) { val available:Long get() = FinanceActivityPolicy.remainingAdvance(amount,allocated) }
    data class Event(
        val id:String, val date:String, val type:String, val title:String, val detail:String,
        val amount:Long, val vendorId:String, val vendor:String, val billId:String? = null,
        val cashMovement:Boolean = false, val stockPending:Boolean = false
    )
    data class RevenueInput(val sourceId:String,val propertyId:String,val date:String,val department:String,
                            val amountPaise:Long,val reference:String)
    data class Overview(val billed:Long,val settled:Long,val due:Long,val advances:Long,val revenue:Long)

    override fun onConfigure(db:SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE finance_bills(
            id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL,
            vendor_id TEXT NOT NULL, vendor TEXT NOT NULL, department TEXT NOT NULL,
            bill_date TEXT NOT NULL, due_date TEXT NOT NULL, period TEXT NOT NULL,
            amount_paise INTEGER NOT NULL CHECK(amount_paise>0),
            opening_paid INTEGER NOT NULL DEFAULT 0 CHECK(opening_paid>=0),
            stock_pending INTEGER NOT NULL DEFAULT 0,
            active INTEGER NOT NULL DEFAULT 1,
            updated_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE finance_payments(
            id TEXT PRIMARY KEY, source_id TEXT UNIQUE, vendor_id TEXT NOT NULL,
            vendor TEXT NOT NULL, payment_date TEXT NOT NULL,
            amount_paise INTEGER NOT NULL CHECK(amount_paise>0),
            method TEXT NOT NULL, reference TEXT NOT NULL, kind TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE finance_allocations(
            id TEXT PRIMARY KEY, payment_id TEXT NOT NULL REFERENCES finance_payments(id),
            bill_id TEXT NOT NULL REFERENCES finance_bills(id),
            amount_paise INTEGER NOT NULL CHECK(amount_paise>0), applied_on TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE finance_revenue(
            source_id TEXT PRIMARY KEY, property_id TEXT NOT NULL, revenue_date TEXT NOT NULL,
            department TEXT NOT NULL, amount_paise INTEGER NOT NULL,
            reference TEXT NOT NULL, received_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE finance_activity_audit(
            id TEXT PRIMARY KEY, record_id TEXT NOT NULL, action TEXT NOT NULL,
            detail TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE finance_payment_reversals(
            payment_id TEXT PRIMARY KEY REFERENCES finance_payments(id),
            reason TEXT NOT NULL, reversed_on TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX finance_bills_vendor_idx ON finance_bills(vendor_id,due_date)")
        db.execSQL("CREATE INDEX finance_allocations_bill_idx ON finance_allocations(bill_id)")
        db.execSQL("CREATE INDEX finance_payments_vendor_idx ON finance_payments(vendor_id,payment_date)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        if(oldVersion==1 && newVersion==2) {
            db.execSQL("""CREATE TABLE finance_payment_reversals(
                payment_id TEXT PRIMARY KEY REFERENCES finance_payments(id),
                reason TEXT NOT NULL, reversed_on TEXT NOT NULL, created_at INTEGER NOT NULL
            )""")
        } else throw IllegalStateException("Unsupported ledger migration $oldVersion → $newVersion; data preserved")
    }
    private inline fun <T> transaction(body:(SQLiteDatabase)->T):T {
        val db=writableDatabase; db.beginTransaction()
        try { val result=body(db);db.setTransactionSuccessful();return result }
        finally { db.endTransaction() }
    }
    private inline fun <T> select(db:SQLiteDatabase,sql:String,args:Array<String> = emptyArray(),convert:(Cursor)->T):List<T> =
        db.rawQuery(sql,args).use { c -> buildList { while(c.moveToNext())add(convert(c)) } }
    private fun audit(db:SQLiteDatabase,ref:String,action:String,detail:String) {
        db.insertOrThrow("finance_activity_audit",null,ContentValues().apply {
            put("id",UUID.randomUUID().toString());put("record_id",ref);put("action",action)
            put("detail",detail);put("created_at",System.currentTimeMillis())
        })
    }
    private fun existingSettled(db:SQLiteDatabase,id:String):Long = select(db,
        """SELECT COALESCE(SUM(a.amount_paise),0) FROM finance_allocations a
        WHERE a.bill_id=? AND NOT EXISTS(
            SELECT 1 FROM finance_payment_reversals r WHERE r.payment_id=a.payment_id)""",arrayOf(id)) { it.getLong(0) }.single()
    private fun putBill(db:SQLiteDatabase,id:String,source:String,title:String,vendorId:String,vendor:String,
                        department:String,date:String,dueDate:String,period:String,amount:Long,openingPaid:Long,stock:Boolean) {
        LocalDate.parse(date);LocalDate.parse(dueDate)
        require(id.isNotBlank() && vendorId.isNotBlank() && amount>0 && openingPaid>=0)
        val old=select(db,"SELECT amount_paise,opening_paid,vendor_id FROM finance_bills WHERE id=?",arrayOf(id)) {
            Triple(it.getLong(0),it.getLong(1),it.getString(2))
        }.firstOrNull()
        val originalVendorName=if(old==null)null else select(db,
            "SELECT vendor FROM finance_bills WHERE id=?",arrayOf(id)) { it.getString(0) }.firstOrNull()
        val allocated=existingSettled(db,id)
        require(old==null || source!="MANUAL" || allocated==0L || old.second==openingPaid) {
            "Earlier paid amount changed after recorded payments. Existing history was preserved; accountant review required."
        }
        require(old==null || allocated==0L || old.third==vendorId) {
            "Cannot reassign vendor after payments; correction/reversal required"
        }
        require(Math.addExact(openingPaid,allocated)<=amount) {
            "Bill $title changed below existing payments. Review this bill; existing payments were preserved."
        }
        val values=ContentValues().apply {
            put("id",id);put("source",source);put("title",title);put("vendor_id",vendorId)
            // Vendor master renames must not rewrite the supplier name printed on historic bills.
            put("vendor",if(old!=null && old.third==vendorId)originalVendorName?:vendor else vendor)
            put("department",department);put("bill_date",date)
            put("due_date",dueDate);put("period",period);put("amount_paise",amount)
            put("opening_paid",openingPaid);put("stock_pending",if(stock)1 else 0)
            put("active",1);put("updated_at",System.currentTimeMillis())
        }
        if(old==null) { db.insertOrThrow("finance_bills",null,values); audit(db,id,"BILL_IMPORTED","$source · $date") }
        else db.update("finance_bills",values,"id=?",arrayOf(id))
    }
    /** Synchronizes existing V16 records without copying or editing their source databases. */
    fun synchronize(drafts:List<FinanceDemoDraftBill> = emptyList(),includeManual:Boolean = true) {
        val fixed=FinanceFixedExpenseStore(contextForStore).use { original ->
            original.issueThrough(YearMonth.now())
            val rules=original.rules().associateBy { it.id }
            original.bills().map { bill -> Triple(bill,rules[bill.ruleId]?.vendorId ?: bill.vendor,
                original.payments(bill.id)) }
        }
        val names=if(includeManual) HotelPurchaseVendorStore(contextForStore).allIncludingArchived().associate { it.id to it.name } else emptyMap()
        val historical=FinanceDemoDraftPolicy.vendors.associate { it.code to it.name }
        val stock=if(includeManual) FinanceExpenseStockLinkStore(contextForStore).all()
            .filter { it.state==FinanceExpenseStockPolicy.PENDING }.map { it.draftId }.toSet() else emptySet()
        val issuedPayroll=FinancePayrollStore(contextForStore).use { payroll ->
            payroll.issuedRuns().map { run ->
                run to (payroll.employee(run.employeeId) ?: error("Payroll employee missing"))
            }
        }
        val approvedCompanyExitBenefits=FinancePayrollStore(contextForStore).use { payroll ->
            payroll.approvedCompanyExitBenefits()
        }
        transaction { db ->
            if(includeManual) drafts.forEach { d ->
                val vendorName=when {
                    d.vendorCode.startsWith("one_time:")->d.vendorCode.removePrefix("one_time:")
                    d.vendorCode=="no_payee"->"No payee"
                    else->names[d.vendorCode] ?: historical[d.vendorCode] ?: "Unknown vendor"
                }
                if(d.amountPaise>0) putBill(db,"manual:${d.draftId}","MANUAL",d.expenseTypeLabel ?: d.category,
                    d.vendorCode,vendorName,d.department,d.invoiceDate,d.invoiceDate,d.accountingPeriod,
                    d.amountPaise,d.amountPaidPaise,d.draftId in stock)
            }
            if(includeManual) {
                val incoming=drafts.map { "manual:${it.draftId}" }.toSet()
                select(db,"SELECT id FROM finance_bills WHERE source='MANUAL' AND active=1") { it.getString(0) }
                    .filter { it !in incoming }.forEach { missing ->
                        require(existingSettled(db,missing)==0L) {
                            "Paid draft disappeared from the source. Ledger retained it for review: $missing"
                        }
                        db.update("finance_bills",ContentValues().apply { put("active",0) },"id=?",arrayOf(missing))
                        audit(db,missing,"SOURCE_DRAFT_DISCARDED","Source draft no longer active; no ledger payment allocations")
                    }
            }
            fixed.forEach { (bill,vendorId,payments) ->
                putBill(db,bill.id,"FIXED",bill.type,vendorId,bill.vendor,bill.department,
                    bill.dueDate,bill.dueDate,bill.month,bill.amount,0L,false)
                payments.forEach { p ->
                    val id="legacy-fixed:${p.id}"
                    val new=db.insertWithOnConflict("finance_payments",null,ContentValues().apply {
                        put("id",id);put("source_id",id);put("vendor_id",vendorId);put("vendor",bill.vendor)
                        put("payment_date",p.date);put("amount_paise",p.amount)
                        put("method",p.method);put("reference",p.reference);put("kind","PAYMENT")
                        put("created_at",System.currentTimeMillis())
                    },SQLiteDatabase.CONFLICT_IGNORE)
                    if(new!=-1L) {
                        db.insertOrThrow("finance_allocations",null,ContentValues().apply {
                            put("id",UUID.randomUUID().toString());put("payment_id",id);put("bill_id",bill.id)
                            put("amount_paise",p.amount);put("applied_on",p.date)
                            put("created_at",System.currentTimeMillis())
                        })
                        audit(db,bill.id,"LEGACY_PAYMENT_IMPORTED","Original fixed payment ${p.id} on ${p.date}")
                    }
                }
            }
            // Only issued payroll becomes a bill. Salary advances remain separate payment entries
            // until explicitly allocated to the issued salary bill.
            issuedPayroll.forEach { (run,employee) ->
                val month=YearMonth.parse(run.period)
                val end=(employee.left?.let(LocalDate::parse)
                    ?.takeIf { YearMonth.from(it)==month } ?: month.atEndOfMonth()).toString()
                putBill(db,run.id,"PAYROLL","Salary · ${employee.name}",
                    "employee:${employee.id}",employee.name,employee.department,
                    end,run.dueDate,run.period,run.amountPaise,0L,false)
            }
            // A separate, explicit company-only exit benefit is issued once, with a stable ID.
            // The manager-approved exit review is immutable; statutory leave remains outside
            // this simplified DEV ledger and is NEVER forfeited by notice choice.
            approvedCompanyExitBenefits.forEach { (review,employee) ->
                putBill(db,"payroll-company-exit:${employee.id}","PAYROLL",
                    "Company leave benefit · ${employee.name}","employee:${employee.id}",
                    employee.name,employee.department,review.lastDay,review.lastDay,
                    YearMonth.from(LocalDate.parse(review.lastDay)).toString(),
                    review.companyAmountPaise,0L,false)
            }
        }
    }

    fun bills():List<Bill> = select(readableDatabase,"""
        SELECT b.id,b.source,b.title,b.vendor_id,b.vendor,b.department,b.bill_date,b.due_date,
        b.period,b.amount_paise,b.opening_paid,
        COALESCE((SELECT SUM(a.amount_paise) FROM finance_allocations a
            WHERE a.bill_id=b.id AND NOT EXISTS(
                SELECT 1 FROM finance_payment_reversals r WHERE r.payment_id=a.payment_id)),0),b.stock_pending
        FROM finance_bills b WHERE b.active=1 ORDER BY b.bill_date DESC,b.id
    """.trimIndent()) { c -> Bill(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
        c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),
        c.getLong(9),c.getLong(10),c.getLong(11),c.getInt(12)==1) }
    fun bill(id:String):Bill?=bills().firstOrNull { it.id==id }
    fun payments(includeReversed:Boolean=false):List<Payment> = select(readableDatabase,"""
        SELECT p.id,p.vendor_id,p.vendor,p.payment_date,p.amount_paise,p.method,p.reference,p.kind,
        COALESCE((SELECT SUM(a.amount_paise) FROM finance_allocations a WHERE a.payment_id=p.id),0)
        FROM finance_payments p
        ${if(includeReversed) "" else "WHERE NOT EXISTS(SELECT 1 FROM finance_payment_reversals r WHERE r.payment_id=p.id)"}
        ORDER BY p.payment_date DESC,p.created_at DESC
    """.trimIndent()) { c -> Payment(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
        c.getLong(4),c.getString(5),c.getString(6),c.getString(7),c.getLong(8)) }
    /** One posted salary bill: distinguish prior advances from actual later cash salary payments.
     * These are projections of the existing allocations, not new money movements. */
    data class SalarySettlement(val advancePaise:Long,val paidLaterPaise:Long)
    fun salarySettlement(billId:String):SalarySettlement = select(readableDatabase,"""
        SELECT COALESCE(SUM(CASE WHEN p.kind='ADVANCE' THEN a.amount_paise ELSE 0 END),0),
               COALESCE(SUM(CASE WHEN p.kind='PAYMENT' THEN a.amount_paise ELSE 0 END),0)
        FROM finance_allocations a JOIN finance_payments p ON p.id=a.payment_id
        WHERE a.bill_id=? AND NOT EXISTS(
            SELECT 1 FROM finance_payment_reversals r WHERE r.payment_id=p.id)
    """.trimIndent(),arrayOf(billId)) { c -> SalarySettlement(c.getLong(0),c.getLong(1)) }.single()

    fun outstanding(asOf:LocalDate=LocalDate.now()):List<Bill> = bills().filter {
        !LocalDate.parse(it.dueDate).isAfter(asOf) && it.due>0L
    }.sortedWith(compareBy<Bill> { it.dueDate }.thenBy { it.id })

    /** All new bill settlements are written HERE; one bank/UPI payment, one allocation. */
    fun payBill(billId:String,amount:Long,date:String,method:String,reference:String):String = transaction { db ->
        val dateValue=LocalDate.parse(date)
        FinancePeriodEntryLock(contextForStore).requireDateOpen(date)
        require(!dateValue.isAfter(LocalDate.now())) { "Future payments cannot be marked as completed" }
        require(method.trim().isNotEmpty())
        val b=select(db,"SELECT vendor_id,vendor,amount_paise,opening_paid,bill_date FROM finance_bills WHERE id=?",arrayOf(billId)) {
            listOf(it.getString(0),it.getString(1),it.getLong(2).toString(),it.getLong(3).toString(),it.getString(4))
        }.singleOrNull() ?: error("Bill not found")
        require(!dateValue.isBefore(LocalDate.parse(b[4]))) {
            "Payment predates the recorded bill. Record it as a vendor advance and allocate it later."
        }
        val due=FinanceActivityPolicy.due(b[2].toLong(),b[3].toLong(),existingSettled(db,billId))
        FinanceActivityPolicy.validateSettlement(amount,due)
        checkDuplicateReference(db,b[0],method,reference)
        val paymentId=UUID.randomUUID().toString()
        newPayment(db,paymentId,null,b[0],b[1],date,amount,method,reference,"PAYMENT")
        newAllocation(db,paymentId,billId,amount,date)
        audit(db,paymentId,"PAYMENT","$billId · $amount paise · $date · $method")
        paymentId
    }
    /** Money sent before a bill exists. Cash moves, but no expense or payable is settled yet. */
    fun advance(vendorId:String,vendor:String,amount:Long,date:String,method:String,reference:String):String = transaction { db ->
        require(vendorId.isNotBlank() && vendor.isNotBlank() && amount>0)
        require(!LocalDate.parse(date).isAfter(LocalDate.now()))
        FinancePeriodEntryLock(contextForStore).requireDateOpen(date)
        checkDuplicateReference(db,vendorId,method,reference)
        val id=UUID.randomUUID().toString()
        newPayment(db,id,null,vendorId,vendor,date,amount,method,reference,"ADVANCE")
        audit(db,id,if(vendorId.startsWith("employee:"))"EMPLOYEE_ADVANCE" else "VENDOR_ADVANCE","$vendor · $amount paise · $date")
        id
    }
    private fun checkDuplicateReference(db:SQLiteDatabase,vendorId:String,method:String,reference:String) {
        if(reference.isBlank())return
        val count=select(db,"""SELECT COUNT(*) FROM finance_payments
            WHERE vendor_id=? AND method=? AND reference=?""",arrayOf(vendorId,method.trim(),reference.trim())) { it.getInt(0) }.single()
        require(count==0) { "Reference already recorded for this vendor and method" }
    }
    private fun newPayment(db:SQLiteDatabase,id:String,sourceId:String?,vendorId:String,vendor:String,date:String,
                           amount:Long,method:String,reference:String,kind:String) {
        db.insertOrThrow("finance_payments",null,ContentValues().apply {
            put("id",id);put("source_id",sourceId);put("vendor_id",vendorId);put("vendor",vendor)
            put("payment_date",date);put("amount_paise",amount);put("method",method.trim())
            put("reference",reference.trim());put("kind",kind);put("created_at",System.currentTimeMillis())
        })
    }
    private fun newAllocation(db:SQLiteDatabase,paymentId:String,billId:String,amount:Long,date:String) {
        db.insertOrThrow("finance_allocations",null,ContentValues().apply {
            put("id",UUID.randomUUID().toString());put("payment_id",paymentId);put("bill_id",billId)
            put("amount_paise",amount);put("applied_on",date);put("created_at",System.currentTimeMillis())
        })
    }
    /** Apply an earlier advance to a specific bill, without recording a second cash payment. */
    fun allocateAdvance(paymentId:String,billId:String,amount:Long,appliedOn:String):Unit = transaction { db ->
        require(!LocalDate.parse(appliedOn).isAfter(LocalDate.now()))
        FinancePeriodEntryLock(contextForStore).requireDateOpen(appliedOn)
        val p=select(db,"SELECT vendor_id,kind,amount_paise FROM finance_payments WHERE id=?",arrayOf(paymentId)) {
            Triple(it.getString(0),it.getString(1),it.getLong(2))
        }.singleOrNull() ?: error("Advance not found")
        require(p.second=="ADVANCE") { "Only vendor advances can be adjusted" }
        require(select(db,"SELECT COUNT(*) FROM finance_payment_reversals WHERE payment_id=?",arrayOf(paymentId)) { it.getInt(0) }.single()==0) {
            "This advance was reversed"
        }
        val b=select(db,"SELECT vendor_id,amount_paise,opening_paid FROM finance_bills WHERE id=?",arrayOf(billId)) {
            Triple(it.getString(0),it.getLong(1),it.getLong(2))
        }.singleOrNull() ?: error("Bill not found")
        require(p.first==b.first) { "Advance belongs to another vendor" }
        val used=select(db,"SELECT COALESCE(SUM(amount_paise),0) FROM finance_allocations WHERE payment_id=?",arrayOf(paymentId)) { it.getLong(0) }.single()
        val available=FinanceActivityPolicy.remainingAdvance(p.third,used)
        val due=FinanceActivityPolicy.due(b.second,b.third,existingSettled(db,billId))
        FinanceActivityPolicy.validateSettlement(amount,minOf(available,due))
        newAllocation(db,paymentId,billId,amount,appliedOn)
        audit(db,paymentId,"ADVANCE_APPLIED","$amount paise → $billId · $appliedOn")
    }
    /** Resolve an event's immutable allocation key back to its underlying payment. */
    fun paymentIdForEvent(eventId:String):String? = when {
        eventId.startsWith("advance:")->eventId.removePrefix("advance:")
        eventId.startsWith("allocation:")->select(readableDatabase,
            "SELECT payment_id FROM finance_allocations WHERE id=?",
            arrayOf(eventId.removePrefix("allocation:"))) { it.getString(0) }.singleOrNull()
        else->null
    }
    /** Correct an erroneous locally recorded payment. Keep the original entry plus reversal.
     * No actual bank refund, journal entry or transfer occurs here.
     * Legacy fixed-bill payments are immutable until corrected in their original source. */
    fun reversePayment(paymentId:String,reason:String) = transaction { db ->
        FinancePeriodEntryLock(contextForStore).requireDateOpen(LocalDate.now().toString())
        require(reason.trim().length>=8) { "Provide a specific reversal reason (at least 8 characters)" }
        val payment=select(db,"SELECT source_id,kind,vendor_id,amount_paise FROM finance_payments WHERE id=?",arrayOf(paymentId)) {
            listOf(if(it.isNull(0)) "" else it.getString(0),it.getString(1),it.getString(2),it.getLong(3).toString())
        }.singleOrNull() ?: error("Payment not found")
        require(payment[0].isBlank() && !paymentId.startsWith("legacy-fixed:")) {
            "Legacy payment must be corrected in its original fixed expense records"
        }
        require(select(db,"SELECT COUNT(*) FROM finance_payment_reversals WHERE payment_id=?",arrayOf(paymentId)) {
            it.getInt(0)
        }.single()==0) { "This payment was already reversed" }
        if(payment[1]=="ADVANCE")require(select(db,
            "SELECT COUNT(*) FROM finance_allocations WHERE payment_id=?",arrayOf(paymentId)) { it.getInt(0) }.single()==0) {
            "Allocated advances cannot be cancelled; accountant review and adjustment required"
        }
        db.insertOrThrow("finance_payment_reversals",null,ContentValues().apply {
            put("payment_id",paymentId);put("reason",reason.trim());put("reversed_on",LocalDate.now().toString())
            put("created_at",System.currentTimeMillis())
        })
        audit(db,paymentId,"PAYMENT_REVERSAL",reason.trim())
    }
    /** Idempotent downstream import contract, NOT an authenticated connection to Booking Register. */
    fun importRevenueFromBookingRegister(item:RevenueInput) = transaction { db ->
        require(item.sourceId.isNotBlank() && item.propertyId.isNotBlank() && item.department.isNotBlank())
        LocalDate.parse(item.date)
        require(item.amountPaise!=0L)
        val old=select(db,"SELECT property_id,revenue_date,department,amount_paise FROM finance_revenue WHERE source_id=?",arrayOf(item.sourceId)) {
            listOf(it.getString(0),it.getString(1),it.getString(2),it.getLong(3).toString())
        }.firstOrNull()
        if(old!=null) {
            require(old==listOf(item.propertyId,item.date,item.department,item.amountPaise.toString())) {
                "Source event changed; send an explicit reversal with a new source ID"
            }
        } else {
            db.insertOrThrow("finance_revenue",null,ContentValues().apply {
                put("source_id",item.sourceId);put("property_id",item.propertyId)
                put("revenue_date",item.date);put("department",item.department)
                put("amount_paise",item.amountPaise);put("reference",item.reference)
                put("received_at",System.currentTimeMillis())
            })
            audit(db,item.sourceId,"REVENUE_IMPORTED","Booking Register · ${item.department} · ${item.date}")
        }
    }
    /** Read-only monthly revenue import snapshot. Does NOT fetch or authenticate live PMS data. */
    data class RevenueRecord(val date: String, val department: String, val amountPaise: Long)
    fun revenueRecords(period: String): List<RevenueRecord> {
        val start = YearMonth.parse(period).atDay(1).toString()
        val finish = YearMonth.parse(period).plusMonths(1).atDay(1).toString()
        return select(readableDatabase, """
            SELECT revenue_date,department,amount_paise FROM finance_revenue
            WHERE revenue_date>=? AND revenue_date<? ORDER BY revenue_date,source_id
        """.trimIndent(), arrayOf(start,finish)) { c ->
            RevenueRecord(c.getString(0),c.getString(1),c.getLong(2))
        }
    }

    /** All locally imported revenue records, for ALL TIME read-only reporting. */
    fun revenueRecordsAll(): List<RevenueRecord> = select(readableDatabase, """
        SELECT revenue_date,department,amount_paise FROM finance_revenue
        WHERE revenue_date<=? ORDER BY revenue_date,source_id
    """.trimIndent(), arrayOf(LocalDate.now().toString())) { c ->
        RevenueRecord(c.getString(0),c.getString(1),c.getLong(2))
    }

    fun events():List<Event> {
        val results=mutableListOf<Event>()
        bills().forEach { b ->
            results.add(Event("bill:${b.id}",b.date,"BILL",b.title,"${b.vendor} · ${b.department} · ${b.period}",
                b.amount,b.vendorId,b.vendor,b.id,false,b.stockPending))
            // Legacy manual drafts only held a paid total; the actual date is unavailable.
            // Do not synthesize payment events on the invoice date.
        }
        val allocations=select(readableDatabase,"""
            SELECT a.id,a.payment_id,a.bill_id,a.amount_paise,a.applied_on,
                p.kind,p.method,p.reference,p.vendor_id,p.vendor,b.title
            FROM finance_allocations a JOIN finance_payments p ON p.id=a.payment_id
            JOIN finance_bills b ON b.id=a.bill_id
        """.trimIndent()) { c ->
            val kind=c.getString(5)
            Event("allocation:${c.getString(0)}",if(kind=="ADVANCE")c.getString(4) else
                  select(readableDatabase,"SELECT payment_date FROM finance_payments WHERE id=?",arrayOf(c.getString(1))) { it.getString(0) }.single(),
                if(kind=="ADVANCE")"ADJUSTMENT" else "PAYMENT",
                if(kind=="ADVANCE")"Advance adjusted" else "Payment · ${c.getString(10)}",
                "${c.getString(9)} · ${c.getString(6)}"+(if(c.getString(7).isNotBlank())" · ${c.getString(7)}" else ""),
                c.getLong(3),c.getString(8),c.getString(9),c.getString(2),kind!="ADVANCE")
        }
        results.addAll(allocations)
        payments(includeReversed=true).filter { it.kind=="ADVANCE" }.forEach { p ->
            results.add(Event("advance:${p.id}",p.date,"ADVANCE",if(p.vendorId.startsWith("employee:"))"Salary advance · ${p.vendor}" else "Vendor advance · ${p.vendor}",
                "${p.method}"+(if(p.reference.isNotEmpty())" · ${p.reference}" else ""),p.amount,
                p.vendorId,p.vendor,null,true))
        }
        results.addAll(select(readableDatabase,"""
            SELECT r.payment_id,r.reversed_on,r.reason,p.payment_date,p.vendor_id,p.vendor,p.kind,p.amount_paise
            FROM finance_payment_reversals r JOIN finance_payments p ON p.id=r.payment_id
        """.trimIndent()) { c -> Event("reversal:${c.getString(0)}",c.getString(1),"REVERSAL",
            "Reversed ${if(c.getString(6)=="ADVANCE") "advance" else "payment"} · ${c.getString(5)}",
            "Original ${c.getString(3)} · ${c.getString(6)} · ${c.getString(2)}",
            c.getLong(7),c.getString(4),c.getString(5),
            select(readableDatabase,"SELECT bill_id FROM finance_allocations WHERE payment_id=? LIMIT 1",
                arrayOf(c.getString(0))) { it.getString(0) }.firstOrNull(),false) })
        results.addAll(select(readableDatabase,"""
            SELECT source_id,revenue_date,department,amount_paise,reference FROM finance_revenue
        """.trimIndent()) { c -> Event("revenue:${c.getString(0)}",c.getString(1),"REVENUE",
            "${c.getString(2)} revenue","Booking Register · ${c.getString(4)}",c.getLong(3),"","",null,false) })
        return results.sortedWith(compareByDescending<Event> { it.date }
            .thenBy { when(it.type) { "BILL"->0; "ADVANCE"->1; "PAYMENT"->2; "ADJUSTMENT"->3; else->4 } }
            .thenBy { it.id })
    }
    fun overview(asOf:LocalDate=LocalDate.now()):Overview {
        val active=bills().filter { !LocalDate.parse(it.date).isAfter(asOf) }
        val totals=FinanceActivityPolicy.totals(active.map { it.amount to it.paid })
        val advances=payments().filter { it.kind=="ADVANCE" && !LocalDate.parse(it.date).isAfter(asOf) }
            .sumOf { it.available }
        val revenues=select(readableDatabase,"SELECT COALESCE(SUM(amount_paise),0) FROM finance_revenue WHERE revenue_date<=?",arrayOf(asOf.toString())) { it.getLong(0) }.single()
        return Overview(totals.billed,totals.paid,totals.due,advances,revenues)
    }
}

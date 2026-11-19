package com.example.bookingregister.finance.dev

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** V18 device-only employee payroll master and approved monthly salary snapshots. */
internal class FinancePayrollStore(context: Context) : SQLiteOpenHelper(
    context, "finance_dev_payroll_v18.db", null, 5
) {
    data class Employee(
        val id:String, val name:String, val department:String, val property:String,
        val monthlyPaise:Long, val joined:String, val left:String?,
        val restAllowance:Int, val payDay:Int, val active:Boolean,
        val designation:String = ""
    )
    data class Bonus(
        val id:String, val employeeId:String, val period:String, val date:String,
        val label:String, val amountPaise:Long, val active:Boolean, val voidReason:String
    )
    data class AttendanceMonth(
        val period:String, val first:LocalDate, val last:LocalDate,
        val days:Map<LocalDate,String>, val covered:Int,
        val present:Int, val absent:Int, val unmarked:Int, val closed:Boolean
    )
    data class LeaveMonthRecord(
        val employeeId:String,val period:String,val nonWorking:Int,
        val approvedExtraPaid:Int,val note:String,val updatedAt:Long,val confirmed:Boolean
    )
    data class ExitRecord(
        val employeeId:String,val lastDay:String,val noticeCompleted:Boolean,
        val companyDays:Int,val companyAmountPaise:Long,val unresolvedExtra:Int,
        val reason:String,val recordedAt:Long
    )
    data class ExitPreview(
        val balance:FinanceLeaveBankPolicy.Balance,
        val estimate:FinanceLeaveBankPolicy.ExitEstimate,
        val missingMonths:List<String>,val firstTrackedMonth:String?
    )
    data class Run(
        val id:String, val employeeId:String, val period:String, val nonWorking:Int,
        val extraPaid:Int, val adjustmentPaise:Long, val covered:Int,
        val paidRest:Int, val unpaid:Int, val amountPaise:Long,
        val dueDate:String, val status:String, val reason:String
    )
    override fun onConfigure(db:SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE payroll_employees(
            id TEXT PRIMARY KEY, name TEXT NOT NULL, department TEXT NOT NULL,
            property TEXT NOT NULL, designation TEXT NOT NULL DEFAULT '', monthly_paise INTEGER NOT NULL CHECK(monthly_paise>0),
            joined TEXT NOT NULL, left_on TEXT, rest_allowance INTEGER NOT NULL,
            pay_day INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE payroll_runs(
            id TEXT PRIMARY KEY, employee_id TEXT NOT NULL REFERENCES payroll_employees(id),
            period TEXT NOT NULL, non_working INTEGER NOT NULL, extra_paid INTEGER NOT NULL,
            adjustment_paise INTEGER NOT NULL, covered INTEGER NOT NULL,
            paid_rest INTEGER NOT NULL, unpaid INTEGER NOT NULL,
            amount_paise INTEGER NOT NULL CHECK(amount_paise>=0),
            due_date TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('DRAFT','ISSUED')),
            reason TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            UNIQUE(employee_id,period)
        )""")
        db.execSQL("""CREATE TABLE payroll_audit(
            id TEXT PRIMARY KEY, record_id TEXT NOT NULL,
            action TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX payroll_runs_period_idx ON payroll_runs(period,status)")
        createLeaveTables(db)
        createBonusTable(db)
        createSalaryHistoryTable(db)
        createAttendanceTable(db)
    }
    private fun createLeaveTables(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_company_leave_months(
            employee_id TEXT NOT NULL REFERENCES payroll_employees(id),
            period TEXT NOT NULL, non_working INTEGER NOT NULL CHECK(non_working>=0),
            approved_extra_paid INTEGER NOT NULL CHECK(approved_extra_paid>=0),
            note TEXT NOT NULL, created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
            confirmed INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY(employee_id,period)
        )""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_exit_reviews(
            employee_id TEXT PRIMARY KEY REFERENCES payroll_employees(id),
            last_day TEXT NOT NULL,notice_completed INTEGER NOT NULL,
            company_days INTEGER NOT NULL, company_amount_paise INTEGER NOT NULL,
            unresolved_extra INTEGER NOT NULL,reason TEXT NOT NULL,recorded_at INTEGER NOT NULL
        )""")
    }
    private fun createBonusTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_bonuses(
            id TEXT PRIMARY KEY, employee_id TEXT NOT NULL REFERENCES payroll_employees(id),
            period TEXT NOT NULL, bonus_date TEXT NOT NULL, title TEXT NOT NULL,
            amount_paise INTEGER NOT NULL CHECK(amount_paise>0),
            active INTEGER NOT NULL DEFAULT 1, void_reason TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL, voided_at INTEGER
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS payroll_bonuses_month_idx ON payroll_bonuses(employee_id,period,active)")
    }
    /** Monthly salary rate is preserved by effective month; issued runs remain immutable. */
    private fun createSalaryHistoryTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_salary_rates(
            employee_id TEXT NOT NULL REFERENCES payroll_employees(id),
            effective_month TEXT NOT NULL, monthly_paise INTEGER NOT NULL CHECK(monthly_paise>0),
            reason TEXT NOT NULL, recorded_at INTEGER NOT NULL,
            PRIMARY KEY(employee_id,effective_month)
        )""")
        db.execSQL("""INSERT OR IGNORE INTO payroll_salary_rates
            (employee_id,effective_month,monthly_paise,reason,recorded_at)
            SELECT id,substr(joined,1,7),monthly_paise,'Original agreed salary (imported)',created_at
            FROM payroll_employees""")
    }
    /** One row per employee/day. Absence and presence are explicit; unmarked is not presence. */
    private fun createAttendanceTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_attendance_days(
            employee_id TEXT NOT NULL REFERENCES payroll_employees(id),
            day TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('P','A')),
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(employee_id,day)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS payroll_attendance_day_idx ON payroll_attendance_days(day)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        if(oldVersion !in 1..4 || newVersion!=5) {
            error("Unsupported payroll migration $oldVersion to $newVersion. No records deleted")
        }
        if(oldVersion<=2) {
            if(oldVersion==1) {
                createLeaveTables(db)
                // Imported legacy leave observations need manager confirmation.
                db.execSQL("""INSERT OR IGNORE INTO payroll_company_leave_months
                    (employee_id,period,non_working,approved_extra_paid,note,created_at,updated_at,confirmed)
                    SELECT employee_id,period,non_working,extra_paid,
                        'Imported from old payroll: manager confirmation required',
                        created_at,updated_at,0 FROM payroll_runs""")
            }
            db.execSQL("ALTER TABLE payroll_employees ADD COLUMN designation TEXT NOT NULL DEFAULT ''")
            createBonusTable(db)
        }
        if(oldVersion<=3) createSalaryHistoryTable(db)
        createAttendanceTable(db)
    }
    private inline fun <T> tx(block:(SQLiteDatabase)->T):T {
        val db=writableDatabase; db.beginTransaction()
        try { val result=block(db);db.setTransactionSuccessful();return result }
        finally { db.endTransaction() }
    }
    private inline fun <T> rows(db:SQLiteDatabase,sql:String,args:Array<String> = emptyArray(),
                                map:(Cursor)->T):List<T> = db.rawQuery(sql,args).use { cursor ->
        buildList { while(cursor.moveToNext()) add(map(cursor)) }
    }
    private fun audit(db:SQLiteDatabase,id:String,action:String,details:String) {
        db.insertOrThrow("payroll_audit",null,ContentValues().apply {
            put("id",UUID.randomUUID().toString())
            put("record_id",id);put("action",action);put("detail",details)
            put("created_at",System.currentTimeMillis())
        })
    }
    private fun employeeRow(c:Cursor) = Employee(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
        c.getLong(4),c.getString(5),if(c.isNull(6)) null else c.getString(6),
        c.getInt(7),c.getInt(8),c.getInt(9)==1,c.getString(10))
    private val employeeSql="""SELECT id,name,department,property,monthly_paise,joined,left_on,
        rest_allowance,pay_day,active,designation FROM payroll_employees"""
    fun employees():List<Employee> = rows(readableDatabase,"$employeeSql ORDER BY active DESC,name COLLATE NOCASE") { employeeRow(it) }
    fun employee(id:String):Employee?=rows(readableDatabase,"$employeeSql WHERE id=?",arrayOf(id)) {
        employeeRow(it)
    }.singleOrNull()
    fun addEmployee(name:String,department:String,property:String,monthlyPaise:Long,
                    joined:String,left:String?,restAllowance:Int,payDay:Int,designation:String=""):String = tx { db ->
        val joining=LocalDate.parse(joined)
        left?.let { require(!LocalDate.parse(it).isBefore(joining)) }
        require(name.isNotBlank() && department.isNotBlank() && property.isNotBlank())
        require(monthlyPaise>0 && restAllowance in 0..10 && payDay in 1..28)
        val id=UUID.randomUUID().toString()
        db.insertOrThrow("payroll_employees",null,ContentValues().apply {
            put("id",id);put("name",name.trim());put("designation",designation.trim());put("department",department.trim())
            put("property",property.trim());put("monthly_paise",monthlyPaise)
            put("joined",joined);put("left_on",left)
            put("rest_allowance",restAllowance);put("pay_day",payDay)
            put("active",if(left==null) 1 else 0);put("created_at",System.currentTimeMillis())
        })
        db.insertOrThrow("payroll_salary_rates",null,ContentValues().apply {
            put("employee_id",id);put("effective_month",YearMonth.from(joining).toString())
            put("monthly_paise",monthlyPaise);put("reason","Original agreed salary")
            put("recorded_at",System.currentTimeMillis())
        })
        audit(db,id,"EMPLOYEE_CREATED","$name / $department / $joined")
        id
    }
    fun salaryForPeriod(employeeId:String,period:String):Long {
        val month=YearMonth.parse(period).toString()
        return rows(readableDatabase,
            """SELECT monthly_paise FROM payroll_salary_rates WHERE employee_id=?
                AND effective_month<=? ORDER BY effective_month DESC LIMIT 1""",
            arrayOf(employeeId,month)) { it.getLong(0) }.firstOrNull()
            ?: rows(readableDatabase,
                """SELECT monthly_paise FROM payroll_salary_rates WHERE employee_id=?
                    ORDER BY effective_month ASC LIMIT 1""",
                arrayOf(employeeId)) { it.getLong(0) }.firstOrNull()
            ?: employee(employeeId)?.monthlyPaise ?: error("Employee not found")
    }
    fun lastIssuedMonth(employeeId:String):YearMonth? = rows(readableDatabase,
        "SELECT MAX(period) FROM payroll_runs WHERE employee_id=? AND status='ISSUED'",
        arrayOf(employeeId)) { if(it.isNull(0)) null else YearMonth.parse(it.getString(0)) }.single()
    fun latestRateMonth(employeeId:String):YearMonth? = rows(readableDatabase,
        "SELECT MAX(effective_month) FROM payroll_salary_rates WHERE employee_id=?",
        arrayOf(employeeId)) { if(it.isNull(0)) null else YearMonth.parse(it.getString(0)) }.single()

    /** Audited staff changes. Issued salary and historic posted bills are never recalculated. */
    fun editEmployee(employeeId:String,name:String,designation:String,department:String,
                     property:String,joined:String,monthlyPaise:Long,
                     salaryEffectiveMonth:String,reason:String) = tx { db ->
        val previous=employee(employeeId) ?: error("Employee not found")
        val joining=LocalDate.parse(joined.trim())
        val oldJoining=LocalDate.parse(previous.joined)
        val effective=YearMonth.parse(salaryEffectiveMonth.trim())
        require(name.trim().isNotEmpty() && designation.trim().isNotEmpty() &&
            department.trim().isNotEmpty() && property.trim().isNotEmpty()) {
            "Name, designation, department and hotel are required"
        }
        require(monthlyPaise>0L) { "Monthly salary must be positive" }
        require(!joining.isAfter(LocalDate.now())) { "Joining date cannot be in the future" }
        previous.left?.let { require(!joining.isAfter(LocalDate.parse(it))) {
            "Joining date cannot be later than the leaving date"
        } }
        val joiningChanged=joining!=oldJoining
        val salaryChanged=monthlyPaise!=previous.monthlyPaise
        val anyChanged=joiningChanged || salaryChanged || name.trim()!=previous.name ||
            designation.trim()!=previous.designation || department.trim()!=previous.department ||
            property.trim()!=previous.property
        require(anyChanged) { "No staff details changed" }
        require(reason.trim().length>=5) { "Enter a reason for editing staff (at least 5 characters)" }
        require(exitReview(employeeId)==null) { "Final settlement already reviewed. Accountant correction required." }

        val issued=lastIssuedMonth(employeeId)
        if(joiningChanged) {
            val beforeNewJoining=rows(db,
                "SELECT day FROM payroll_attendance_days WHERE employee_id=? AND day<? LIMIT 1",
                arrayOf(employeeId,joining.toString())) { it.getString(0) }
            require(beforeNewJoining.isEmpty()) {
                "Joining date conflicts with recorded attendance (${beforeNewJoining.first()}). Review required."
            }
            // Joining day affects prorating in that month; crossing a month boundary
            // also affects every intervening month. No issued run in that entire range
            // may be changed silently.
            val impactedFrom=minOf(YearMonth.from(oldJoining),YearMonth.from(joining))
            val impactedTo=maxOf(YearMonth.from(oldJoining),YearMonth.from(joining))
            val posted=rows(db,
                """SELECT period FROM payroll_runs WHERE employee_id=? AND status='ISSUED'
                    AND period>=? AND period<=? LIMIT 1""",
                arrayOf(employeeId,impactedFrom.toString(),impactedTo.toString())) { it.getString(0) }
            require(posted.isEmpty()) {
                "Joining date affects issued salary (${posted.first()}). Accountant review required."
            }
            val confirmed=rows(db,
                """SELECT period FROM payroll_company_leave_months WHERE employee_id=?
                    AND confirmed=1 AND period>=? AND period<=? LIMIT 1""",
                arrayOf(employeeId,impactedFrom.toString(),impactedTo.toString())) { it.getString(0) }
            require(confirmed.isEmpty()) { "Joining date affects confirmed leave records. Review required." }
            val bonus=rows(db,
                "SELECT bonus_date FROM payroll_bonuses WHERE employee_id=? AND active=1 AND bonus_date<? LIMIT 1",
                arrayOf(employeeId,joining.toString())) { it.getString(0) }
            require(bonus.isEmpty()) { "Joining date falls after a recorded bonus. Review required." }
        }
        if(salaryChanged) {
            require(!effective.isBefore(YearMonth.from(joining))) {
                "Salary effective month cannot precede joining"
            }
            require(issued==null || effective.isAfter(issued)) {
                "Salary already issued for this or a later month. Choose a later effective month."
            }
            val latest=latestRateMonth(employeeId)
            if(latest!=null && effective.isBefore(latest)) {
                // One initial rate can be moved when correcting the join date before any issue.
                require(joiningChanged && issued==null && latest==YearMonth.from(oldJoining) &&
                    rows(db,"SELECT COUNT(*) FROM payroll_salary_rates WHERE employee_id=?",
                        arrayOf(employeeId)) { it.getInt(0) }.single()==1) {
                    "Existing salary history has a later effective month. Choose that month or later."
                }
            }
        }
        if(joiningChanged) {
            val earliestMonth=rows(db,
                """SELECT MIN(period) FROM payroll_runs WHERE employee_id=? AND status='ISSUED'""",
                arrayOf(employeeId)) { if(it.isNull(0)) null else it.getString(0) }.single()
            require(earliestMonth==null || YearMonth.parse(earliestMonth).isAfter(YearMonth.from(joining))) {
                "Joining date overlaps an issued payroll. Accountant review required."
            }
            // If only the initial rate exists, align its validity with the corrected join month.
            val count=rows(db,"SELECT COUNT(*) FROM payroll_salary_rates WHERE employee_id=?",
                arrayOf(employeeId)) { it.getInt(0) }.single()
            if(count==1 && YearMonth.from(oldJoining)!=YearMonth.from(joining)) {
                db.update("payroll_salary_rates",ContentValues().apply {
                    put("effective_month",YearMonth.from(joining).toString())
                },"employee_id=?",arrayOf(employeeId))
            }
        }
        db.update("payroll_employees",ContentValues().apply {
            put("name",name.trim());put("designation",designation.trim())
            put("department",department.trim());put("property",property.trim())
            put("joined",joining.toString());put("monthly_paise",monthlyPaise)
        },"id=?",arrayOf(employeeId))
        if(salaryChanged) {
            db.insertWithOnConflict("payroll_salary_rates",null,ContentValues().apply {
                put("employee_id",employeeId);put("effective_month",effective.toString())
                put("monthly_paise",monthlyPaise);put("reason",reason.trim())
                put("recorded_at",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE).also { require(it!=-1L) { "Salary history update failed" } }
            audit(db,employeeId,"SALARY_RATE_CHANGED",
                "${previous.monthlyPaise} → $monthlyPaise paise from $effective · ${reason.trim()}")
        }
        audit(db,employeeId,"STAFF_EDITED",
            "Name ${previous.name} → ${name.trim()} · Role ${previous.designation} → ${designation.trim()}"+
            " · Department ${previous.department} → ${department.trim()}"+
            " · Hotel ${previous.property} → ${property.trim()}"+
            " · Joined ${previous.joined} → $joining"+
            " · Salary ${previous.monthlyPaise} → $monthlyPaise paise"+
            " · effective $effective · ${reason.trim()}")
    }

    fun changeDesignation(employeeId:String,designation:String,reason:String) = tx { db ->
        require(designation.trim().isNotEmpty()) { "Designation required" }
        require(reason.trim().length>=5) { "Reason for changing employee designation required" }
        val employee=employee(employeeId) ?: error("Employee not found")
        db.update("payroll_employees",ContentValues().apply {
            put("designation",designation.trim())
        },"id=?",arrayOf(employeeId))
        audit(db,employeeId,"DESIGNATION_CHANGED","${employee.designation} → ${designation.trim()} · ${reason.trim()}")
    }
    fun bonuses(employeeId:String,period:String?=null,includeVoided:Boolean=false):List<Bonus> {
        val sql="""SELECT id,employee_id,period,bonus_date,title,amount_paise,active,void_reason
            FROM payroll_bonuses WHERE employee_id=?""" +
            (if(period==null)"" else " AND period=?") +
            (if(includeVoided)"" else " AND active=1") + " ORDER BY bonus_date,id"
        return rows(readableDatabase,sql,
            if(period==null)arrayOf(employeeId) else arrayOf(employeeId,period)) { c ->
            Bonus(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
                c.getString(4),c.getLong(5),c.getInt(6)==1,c.getString(7))
        }
    }
    fun bonusTotal(employeeId:String,period:String):Long =
        bonuses(employeeId,period).fold(0L) { total,bonus -> Math.addExact(total,bonus.amountPaise) }
    fun addBonus(employeeId:String,date:String,title:String,amountPaise:Long,reason:String):String = tx { db ->
        val employee=employee(employeeId) ?: error("Employee not found")
        val chosen=LocalDate.parse(date)
        require(!chosen.isAfter(LocalDate.now()) && !chosen.isBefore(LocalDate.parse(employee.joined))) {
            "Bonus date must be within employment and not in future"
        }
        employee.left?.let { require(!chosen.isAfter(LocalDate.parse(it))) { "Bonus dated after employment ended" } }
        val period=YearMonth.from(chosen).toString()
        require(run(FinancePayrollPolicy.billId(employeeId,period))?.status!="ISSUED") {
            "Salary for this month is already issued. Bonus correction requires a separate approved adjustment."
        }
        require(amountPaise>0L && title.trim().isNotEmpty()) { "Bonus description and positive amount required" }
        require(reason.trim().length>=5) { "Manager approval / reason is required" }
        val id=UUID.randomUUID().toString()
        db.insertOrThrow("payroll_bonuses",null,ContentValues().apply {
            put("id",id);put("employee_id",employeeId);put("period",period)
            put("bonus_date",date);put("title",title.trim());put("amount_paise",amountPaise)
            put("active",1);put("void_reason","");put("created_at",System.currentTimeMillis())
        })
        audit(db,id,"BONUS_CREATED","$period · $title · $amountPaise paise · ${reason.trim()}")
        id
    }
    fun voidBonus(id:String,reason:String) = tx { db ->
        require(reason.trim().length>=8) { "Give a detailed reason for cancelling the bonus" }
        val data=rows(db,"SELECT employee_id,period,active FROM payroll_bonuses WHERE id=?",
            arrayOf(id)) { c -> Triple(c.getString(0),c.getString(1),c.getInt(2)==1) }.singleOrNull()
            ?:error("Bonus not found")
        require(data.third) { "Bonus already cancelled" }
        require(run(FinancePayrollPolicy.billId(data.first,data.second))?.status!="ISSUED") {
            "Issued payroll cannot be changed. Accountant review required."
        }
        db.update("payroll_bonuses",ContentValues().apply {
            put("active",0);put("void_reason",reason.trim());put("voided_at",System.currentTimeMillis())
        },"id=?",arrayOf(id))
        audit(db,id,"BONUS_CANCELLED",reason.trim())
    }
    fun setLeavingDate(employeeId:String,lastDay:String,reason:String) = tx { db ->
        val e=employee(employeeId) ?: error("Employee not found")
        require(reason.isNotBlank()) { "Reason for departure is required" }
        require(!LocalDate.parse(lastDay).isBefore(LocalDate.parse(e.joined)))
        val latestIssued=rows(db,"SELECT MAX(period) FROM payroll_runs WHERE employee_id=? AND status='ISSUED'",
            arrayOf(employeeId)) { if(it.isNull(0)) null else it.getString(0) }.single()
        require(latestIssued==null || YearMonth.parse(latestIssued).isBefore(YearMonth.from(LocalDate.parse(lastDay)))) {
            "Departure date affects issued salary; accountant reversal/review required"
        }
        db.update("payroll_employees",ContentValues().apply {
            put("left_on",lastDay);put("active",0)
        },"id=?",arrayOf(employeeId))
        audit(db,employeeId,"DEPARTURE_RECORDED","$lastDay · ${reason.trim()}")
    }
    private fun runRow(c:Cursor)=Run(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),
        c.getInt(4),c.getLong(5),c.getInt(6),c.getInt(7),c.getInt(8),c.getLong(9),
        c.getString(10),c.getString(11),c.getString(12))
    private val runSql="""SELECT id,employee_id,period,non_working,extra_paid,adjustment_paise,
        covered,paid_rest,unpaid,amount_paise,due_date,status,reason FROM payroll_runs"""
    fun runs(period:String?=null):List<Run> = if(period==null)
        rows(readableDatabase,"$runSql ORDER BY period DESC,employee_id") { runRow(it) }
    else rows(readableDatabase,"$runSql WHERE period=? ORDER BY employee_id",arrayOf(period)) { runRow(it) }
    fun issuedRuns():List<Run> = rows(readableDatabase,"$runSql WHERE status='ISSUED' ORDER BY period,employee_id") { runRow(it) }
    fun run(id:String):Run? = rows(readableDatabase,"$runSql WHERE id=?",arrayOf(id)) { runRow(it) }.singleOrNull()
    fun preview(e:Employee,period:String,nonWorking:Int,extraPaid:Int,adjustment:Long) =
        FinancePayrollPolicy.calculate(YearMonth.parse(period),salaryForPeriod(e.id,period),
            LocalDate.parse(e.joined),e.left?.let(LocalDate::parse),
            nonWorking,e.restAllowance,extraPaid,adjustment)

    /** Draft payroll is editable; salary liability enters Finance only when explicitly issued. */
    fun saveDraft(employeeId:String,period:String,nonWorking:Int,extraPaid:Int,
                  adjustment:Long,reason:String):Run = tx { db ->
        val e=employee(employeeId) ?: error("Employee not found")
        val result=preview(e,period,nonWorking,extraPaid,adjustment)
        require(result.grossPayPaise>0) { "Zero salary requires a separate approved exception" }
        if(extraPaid>0 || adjustment!=0L)require(reason.isNotBlank()) {
            "Reason is mandatory for extra paid days or salary adjustment"
        }
        val id=FinancePayrollPolicy.billId(employeeId,period)
        val existing=run(id)
        require(existing?.status!="ISSUED") { "Salary already issued. Never overwrite posted history." }
        val dueDate=FinancePayrollPolicy.nextPayDate(YearMonth.parse(period),e.payDay).toString()
        val values=ContentValues().apply {
            put("id",id);put("employee_id",employeeId);put("period",period)
            put("non_working",nonWorking);put("extra_paid",extraPaid)
            put("adjustment_paise",adjustment);put("covered",result.coveredDays)
            put("paid_rest",result.paidRestUsed);put("unpaid",result.unpaidDays)
            put("amount_paise",result.grossPayPaise);put("due_date",dueDate)
            put("status","DRAFT");put("reason",reason.trim())
            put("created_at",System.currentTimeMillis());put("updated_at",System.currentTimeMillis())
        }
        if(existing==null)db.insertOrThrow("payroll_runs",null,values)
        else db.update("payroll_runs",values,"id=?",arrayOf(id))
        audit(db,id,if(existing==null)"PAYROLL_DRAFT_CREATED" else "PAYROLL_DRAFT_REVISED",
            "Non-working $nonWorking; extra paid $extraPaid; gross ${result.grossPayPaise} paise; reason ${reason.trim()}")
        run(id) ?: error("Unable to save payroll draft")
    }
    fun issue(runId:String):Run = tx { db ->
        val current=run(runId) ?: error("Salary draft not found")
        require(current.status=="DRAFT") { "Salary is already issued" }
        val e=employee(current.employeeId) ?: error("Payroll employee missing")
        val recalculated=preview(e,current.period,current.nonWorking,current.extraPaid,current.adjustmentPaise)
        require(recalculated.grossPayPaise==current.amountPaise &&
                recalculated.coveredDays==current.covered &&
                recalculated.paidRestUsed==current.paidRest &&
                recalculated.unpaidDays==current.unpaid) {
            "Employee dates or payroll settings changed after this draft. Re-save the payroll preview before issuing."
        }
        val month=YearMonth.parse(current.period)
        val finalDay=e.left?.let(LocalDate::parse)?.takeIf { YearMonth.from(it)==month }
        val earningEnd=finalDay ?: month.atEndOfMonth()
        require(!earningEnd.isAfter(LocalDate.now())) {
            "Issue salary after month-end or the employee's final working date. Earlier payments are advances."
        }
        val n=db.update("payroll_runs",ContentValues().apply {
            put("status","ISSUED");put("updated_at",System.currentTimeMillis())
        },"id=? AND status='DRAFT'",arrayOf(runId))
        check(n==1)
        audit(db,runId,"SALARY_ISSUED","Monthly salary bill ${current.amountPaise} paise · ${current.period}")
        run(runId) ?: error("Salary issue was not persisted")
    }

    /** Staff submit only the total non-working days; no daily attendance is required. */
    /** Employment-day calendar for one month. No future dates or days outside employment are writable. */
    fun attendanceMonth(employeeId:String,period:String):AttendanceMonth {
        val e=employee(employeeId) ?: error("Employee not found")
        val month=YearMonth.parse(period)
        val first=maxOf(LocalDate.parse(e.joined),month.atDay(1))
        val last=minOf(e.left?.let(LocalDate::parse) ?: month.atEndOfMonth(),month.atEndOfMonth())
        require(!first.isAfter(last)) { "Not employed in this month" }
        val days=rows(readableDatabase,
            "SELECT day,status FROM payroll_attendance_days WHERE employee_id=? AND day>=? AND day<=? ORDER BY day",
            arrayOf(employeeId,first.toString(),last.toString())) {
                LocalDate.parse(it.getString(0)) to it.getString(1)
            }.toMap()
        val covered=(last.toEpochDay()-first.toEpochDay()+1).toInt()
        val present=days.values.count { it=="P" }
        val absent=days.values.count { it=="A" }
        return AttendanceMonth(month.toString(),first,last,days,covered,present,absent,
            covered-present-absent,!last.isAfter(LocalDate.now()))
    }
    /** For completed months a correction to a recorded day requires an audit reason. */
    fun setAttendanceDay(employeeId:String,date:String,status:String?,reason:String="") {
        val day=LocalDate.parse(date)
        val month=attendanceMonth(employeeId,YearMonth.from(day).toString())
        require(!day.isAfter(LocalDate.now()) && day>=month.first && day<=month.last) {
            "Attendance day must be employed and not in the future"
        }
        require(status==null || status=="P" || status=="A") { "Invalid attendance status" }
        val previous=month.days[day]
        if(previous==status) return
        require(exitReview(employeeId)==null) { "Exit already approved. Attendance change requires accountant review." }
        if(month.closed && previous!=null) require(reason.trim().length>=5) {
            "Correction to a completed month's attendance needs a reason (5+ characters)"
        }
        tx { db ->
            if(status==null) {
                db.delete("payroll_attendance_days","employee_id=? AND day=?",arrayOf(employeeId,date))
            } else {
                db.insertWithOnConflict("payroll_attendance_days",null,ContentValues().apply {
                    put("employee_id",employeeId);put("day",date);put("status",status)
                    put("updated_at",System.currentTimeMillis())
                },SQLiteDatabase.CONFLICT_REPLACE).also { require(it!=-1L) }
            }
            audit(db,employeeId,"ATTENDANCE_DAY_EDITED",
                "$date · ${previous?:"UNMARKED"} → ${status?:"UNMARKED"} · ${reason.trim().ifBlank { "Calendar tap" }}")
        }
        reconcileAttendanceLeave(employeeId,month.period)
    }
    /** Explicit bulk action, never infers attendance by itself; does not overwrite absences. */
    fun markUnmarkedPresent(employeeId:String,period:String):Int {
        val month=attendanceMonth(employeeId,period)
        require(exitReview(employeeId)==null) { "Exit already approved. Accountant review required." }
        val today=LocalDate.now()
        var count=0
        tx { db ->
            var date=month.first
            while(!date.isAfter(month.last) && !date.isAfter(today)) {
                if(month.days[date]==null) {
                    db.insertOrThrow("payroll_attendance_days",null,ContentValues().apply {
                        put("employee_id",employeeId);put("day",date.toString())
                        put("status","P");put("updated_at",System.currentTimeMillis())
                    })
                    count++
                }
                date=date.plusDays(1)
            }
            if(count>0) audit(db,employeeId,"ATTENDANCE_BULK_PRESENT",
                "$period · $count previously unmarked employed days through $today marked present")
        }
        reconcileAttendanceLeave(employeeId,period)
        return count
    }
    /** Automatic month-end company-credit rollup. Legacy/manual records stay in audit history. */
    fun reconcileAttendanceLeave(employeeId:String,period:String):Boolean {
        val month=attendanceMonth(employeeId,period)
        val old=leaveMonth(employeeId,period)
        // An approved exit settlement is immutable; this read-only sync never reopens it.
        if(exitReview(employeeId)!=null) return old?.confirmed==true && old.note.startsWith("AUTO_ATTENDANCE:")
        if(!month.closed || month.unmarked>0) {
            if(old?.confirmed==true && old.note.startsWith("AUTO_ATTENDANCE:")) tx { db ->
                db.execSQL("UPDATE payroll_company_leave_months SET confirmed=0,updated_at=? WHERE employee_id=? AND period=?",
                    arrayOf(System.currentTimeMillis(),employeeId,period))
                audit(db,employeeId,"ATTENDANCE_MONTH_REOPENED","$period · attendance incomplete: ${month.unmarked} unmarked")
            }
            return false
        }
        if(old?.confirmed==true && old.note.startsWith("AUTO_ATTENDANCE:") &&
            old.nonWorking==month.absent) return true
        val e=employee(employeeId) ?: error("Employee not found")
        val available=FinanceLeaveBankPolicy.month(YearMonth.parse(period),LocalDate.parse(e.joined),
            e.left?.let(LocalDate::parse),e.restAllowance,month.absent,0)
        // Preserve any PREVIOUSLY approved manager exception only when it remains valid.
        val approved=minOf(old?.approvedExtraPaid ?: 0, available.unresolvedExtra)
        saveLeaveMonth(employeeId,period,month.absent,approved,
            "AUTO_ATTENDANCE: $period · ${month.present} present · ${month.absent} absent · ${month.covered} covered; migrated previous approved extra $approved")
        return true
    }
    fun reconcileCompletedAttendance(employeeId:String) {
        val e=employee(employeeId) ?: return
        var month=YearMonth.from(LocalDate.parse(e.joined))
        val limit=minOf(YearMonth.now(),e.left?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now())
        while(!month.isAfter(limit)) {
            reconcileAttendanceLeave(employeeId,month.toString())
            month=month.plusMonths(1)
        }
    }

    fun leaveMonths(employeeId:String):List<LeaveMonthRecord> = rows(readableDatabase,
        """SELECT employee_id,period,non_working,approved_extra_paid,note,updated_at,confirmed
            FROM payroll_company_leave_months WHERE employee_id=? ORDER BY period""",
        arrayOf(employeeId)) { c -> LeaveMonthRecord(c.getString(0),c.getString(1),
            c.getInt(2),c.getInt(3),c.getString(4),c.getLong(5),c.getInt(6)==1) }

    fun leaveMonth(employeeId:String,period:String)=leaveMonths(employeeId).firstOrNull { it.period==period }

    fun saveLeaveMonth(employeeId:String,period:String,nonWorking:Int,
                       managerApprovedExtra:Int,note:String) = tx { db ->
        val e=employee(employeeId) ?: error("Employee not found")
        val month=YearMonth.parse(period)
        require(!month.isAfter(YearMonth.now())) { "Future months cannot be confirmed" }
        require(exitReview(employeeId)==null) { "Exit review is recorded; accountant review required for correction" }
        val summary=FinanceLeaveBankPolicy.month(month,LocalDate.parse(e.joined),
            e.left?.let(LocalDate::parse),e.restAllowance,nonWorking,managerApprovedExtra)
        val existing=leaveMonth(employeeId,period)
        val monthIsClosed=month.atEndOfMonth().isBefore(LocalDate.now())
        if(existing!=null || monthIsClosed || managerApprovedExtra>0 || summary.unresolvedExtra>0) {
            require(note.trim().isNotBlank()) {
                "Reason/manager approval required for completed-month changes or leave exceptions"
            }
        }
        val values=ContentValues().apply {
            put("employee_id",employeeId);put("period",period)
            put("non_working",nonWorking);put("approved_extra_paid",managerApprovedExtra)
            put("note",note.trim());put("updated_at",System.currentTimeMillis())
            put("confirmed",1)
        }
        if(existing==null) {
            values.put("created_at",System.currentTimeMillis())
            db.insertOrThrow("payroll_company_leave_months",null,values)
        } else db.update("payroll_company_leave_months",values,
            "employee_id=? AND period=?",arrayOf(employeeId,period))
        audit(db,employeeId,if(existing==null)"COMPANY_LEAVE_MONTH_CONFIRMED" else "COMPANY_LEAVE_MONTH_REVISED",
            "$period · non-working $nonWorking · manager-paid extra $managerApprovedExtra · reason: ${note.trim()}")
    }

    fun exitReview(employeeId:String):ExitRecord?=rows(readableDatabase,
        """SELECT employee_id,last_day,notice_completed,company_days,
           company_amount_paise,unresolved_extra,reason,recorded_at
           FROM payroll_exit_reviews WHERE employee_id=?""",arrayOf(employeeId)) { c ->
        ExitRecord(c.getString(0),c.getString(1),c.getInt(2)==1,c.getInt(3),
            c.getLong(4),c.getInt(5),c.getString(6),c.getLong(7))
    }.firstOrNull()

    /** Only confirmed months count. Missing periods are shown rather than guessed. */
    fun previewExit(employeeId:String,lastDay:String,noticeCompleted:Boolean):ExitPreview {
        val e=employee(employeeId) ?: error("Employee not found")
        val leaving=LocalDate.parse(lastDay)
        val joined=LocalDate.parse(e.joined)
        require(!leaving.isBefore(joined) && !leaving.isAfter(LocalDate.now())) {
            "Last working day must be between joining date and today"
        }
        e.left?.let { require(it==lastDay) { "Already recorded leaving date must match the settlement review" } }
        val months=leaveMonths(employeeId).filter {
            it.confirmed && it.note.startsWith("AUTO_ATTENDANCE:") &&
                !YearMonth.parse(it.period).isAfter(YearMonth.from(leaving))
        }
        val first=YearMonth.from(joined).toString()
        val missing=mutableListOf<String>()
        if(first!=null) {
            var cursor=YearMonth.parse(first)
            val last=YearMonth.from(leaving)
            val known=months.map { it.period }.toSet()
            while(!cursor.isAfter(last)) {
                if(cursor.toString() !in known) missing.add(cursor.toString())
                cursor=cursor.plusMonths(1)
            }
        } else missing.add(YearMonth.from(leaving).toString())
        val calculated=months.map { record ->
            FinanceLeaveBankPolicy.month(YearMonth.parse(record.period),joined,leaving,
                e.restAllowance,record.nonWorking,record.approvedExtraPaid)
        }
        val balance=FinanceLeaveBankPolicy.balance(calculated)
        val estimate=FinanceLeaveBankPolicy.exitPreview(balance,noticeCompleted,
            salaryForPeriod(employeeId,YearMonth.from(leaving).toString()),leaving)
        return ExitPreview(balance,estimate,missing,first)
    }

    fun approvedCompanyExitBenefits():List<Pair<ExitRecord,Employee>> = employees().mapNotNull { e ->
        exitReview(e.id)?.takeIf { it.companyAmountPaise>0L }?.let { it to e }
    }

    /** Manager confirms only the discretionary company benefit. Statutory leave is unaffected. */
    fun recordExit(employeeId:String,lastDay:String,noticeCompleted:Boolean,reason:String):ExitRecord = tx { db ->
        require(reason.trim().isNotBlank()) { "Exit and notice decision reason is mandatory" }
        val e=employee(employeeId) ?: error("Employee not found")
        require(exitReview(employeeId)==null && (e.active || e.left==lastDay)) { "Exit was already recorded or date conflicts with the employee master" }
        val latestIssued=rows(db,
            "SELECT MAX(period) FROM payroll_runs WHERE employee_id=? AND status='ISSUED'",
            arrayOf(employeeId)) { if(it.isNull(0)) null else it.getString(0) }.single()
        require(latestIssued==null || YearMonth.parse(latestIssued).isBefore(YearMonth.from(LocalDate.parse(lastDay)))) {
            "Salary already issued for the departure month or later. Accountant review required."
        }
        val preview=previewExit(employeeId,lastDay,noticeCompleted)
        require(preview.missingMonths.isEmpty()) {
            "Confirm each leave month first: ${preview.missingMonths.joinToString()}"
        }
        val estimate=preview.estimate
        db.insertOrThrow("payroll_exit_reviews",null,ContentValues().apply {
            put("employee_id",employeeId);put("last_day",lastDay)
            put("notice_completed",if(noticeCompleted)1 else 0)
            put("company_days",estimate.companyBenefitDays)
            put("company_amount_paise",estimate.companyBenefitPaise)
            put("unresolved_extra",estimate.unresolvedExtraDays)
            put("reason",reason.trim());put("recorded_at",System.currentTimeMillis())
        })
        db.update("payroll_employees",ContentValues().apply {
            put("left_on",lastDay);put("active",0)
        },"id=?",arrayOf(employeeId))
        audit(db,employeeId,"EMPLOYEE_EXIT_REVIEWED",
            "Exit $lastDay · notice complete $noticeCompleted · discretionary days ${estimate.companyBenefitDays}"+
            " · discretionary preview ${estimate.companyBenefitPaise} paise · statutory entitlements excluded · ${reason.trim()}")
        exitReview(employeeId) ?: error("Exit review not saved")
    }

}

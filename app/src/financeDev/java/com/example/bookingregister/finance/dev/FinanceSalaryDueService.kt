package com.example.bookingregister.finance.dev

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Device-local salary bill publisher. Allows exactly the month immediately before
 * first activation to be issued on its due date; does not backfill older history.
 * Repeated calls are idempotent (unique payroll run ID).
 * This does not transfer cash or create statutory journal entries.
 */
internal object FinanceSalaryDueService {
    private const val PREFS = "finance_dev_salary_auto_v18"
    private const val FIRST_MONTH = "first_month"

    data class Result(val issued:Int, val duplicateFixed:Int, val reviewNeeded:Int)

    fun issueIfDue(context:Context, today:LocalDate=LocalDate.now()):Result {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val first=prefs.getString(FIRST_MONTH,null) ?: YearMonth.from(today).toString().also {
            require(prefs.edit().putString(FIRST_MONTH,it).commit()) { "Unable to save salary activation month" }
        }
        // Capture the immediately preceding earning month on first activation.
        // Example: Finance first opened in November; October salary due November 2
        // remains eligible. Older pre-activation months are NOT issued automatically.
        // Existing issued runs and conflicting recurring salary bills are checked below.
        val earliest=YearMonth.parse(first).minusMonths(1)
        val latest=YearMonth.from(today).minusMonths(1)
        if(latest.isBefore(earliest))return Result(0,0,0)
        var issued=0
        var duplicates=0
        var review=0
        FinancePayrollStore(context).use { payroll ->
            val staff=payroll.employees()
            FinanceActivityStore(context).use { ledger ->
                ledger.synchronize(includeManual=false)
                var payrollMonth=earliest
                while(!payrollMonth.isAfter(latest)) {
                    val period=payrollMonth.toString()
                    val candidates=staff.filter { employee ->
                        !LocalDate.parse(employee.joined).isAfter(payrollMonth.atEndOfMonth()) &&
                        (employee.left==null || !LocalDate.parse(employee.left).isBefore(payrollMonth.atDay(1))) &&
                        !FinancePayrollPolicy.nextPayDate(payrollMonth,employee.payDay).isAfter(today)
                    }
                    candidates.forEach { employee ->
                        val previous=payroll.runs(period).firstOrNull { it.employeeId==employee.id }
                        if(previous?.status=="ISSUED")return@forEach
                        val fixedDuplicate=ledger.bills().any { bill ->
                            bill.source=="FIXED" && bill.period==period &&
                            bill.vendor.trim().equals(employee.name.trim(),ignoreCase=true) &&
                            (bill.title.contains("salary",true) || bill.title.contains("wage",true))
                        }
                        if(fixedDuplicate) { duplicates++;return@forEach }
                        val bonus=payroll.bonusTotal(employee.id,period)
                        val expected=payroll.preview(employee,period,0,0,bonus)
                        // Never overwrite a customized unissued salary draft.
                        if(previous!=null && (previous.nonWorking!=0 || previous.extraPaid!=0 ||
                            previous.adjustmentPaise!=bonus || previous.amountPaise!=expected.grossPayPaise)) {
                            review++;return@forEach
                        }
                        try {
                            val run=previous ?: payroll.saveDraft(employee.id,period,0,0,bonus,
                                "Scheduled salary bill on contracted payment date; attendance leave bank separate")
                            payroll.issue(run.id)
                            issued++
                        } catch (error:Exception) {
                            review++
                        }
                    }
                    payrollMonth=payrollMonth.plusMonths(1)
                }
                if(issued>0)ledger.synchronize(includeManual=false)
            }
        }
        return Result(issued,duplicates,review)
    }

    /** Reconcile dated employee advances before either payment entry point displays a due. */
    fun reconcileIssuedAdvances(context:Context,today:LocalDate=LocalDate.now()) {
        val issued=FinancePayrollStore(context).use { payroll ->
            payroll.issuedRuns().sortedWith(compareBy({it.period},{it.employeeId}))
        }
        if(issued.isEmpty())return
        FinanceActivityStore(context).use { ledger ->
            ledger.synchronize(includeManual=false)
            issued.forEach { run ->
                val bill=ledger.bill(run.id) ?: return@forEach
                if(bill.due<=0L)return@forEach
                val advances=ledger.payments().filter {
                    it.kind=="ADVANCE" && it.vendorId==bill.vendorId
                }.map { FinanceSalaryAdvancePolicy.Advance(it.id,it.date,it.amount,it.available) }
                FinanceSalaryAdvancePolicy.plan(YearMonth.parse(run.period),bill.due,advances).forEach { item ->
                    ledger.allocateAdvance(item.paymentId,bill.id,item.amount,today.toString())
                }
            }
        }
    }
}

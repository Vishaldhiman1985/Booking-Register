package com.example.bookingregister.finance.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/**
 * V19-V24 consolidated, read-only reconciliation policy.
 * It is not a statutory journal or an authenticated Booking Register integration.
 * All amounts are integer INR paise. No tax amount is silently treated as an expense.
 * The caller must pass actual, already recorded source rows only.
 */
object FinanceV24Reconciliation {
    enum class Severity { BLOCKER, REVIEW }
    data class Issue(val severity: Severity, val code: String, val detail: String)
    data class Bill(
        val id: String, val month: String, val vendorId: String, val source: String,
        val department: String, val amountPaise: Long, val paidPaise: Long,
        val duePaise: Long, val stockPending: Boolean
    )
    data class Payment(
        val id: String, val vendorId: String, val amountPaise: Long,
        val allocatedPaise: Long, val kind: String, val date: String,
        val reference: String
    )
    data class Revenue(val date: String, val department: String, val amountPaise: Long)
    data class LeaseTax(
        val id: String, val month: String, val mode: String,
        val amountPaise: Long, val paidPaise: Long
    )
    data class PayrollRun(val id: String, val month: String, val amountPaise: Long)
    data class StockState(
        val month: String, val approved: Boolean,
        val receiptChangedAfterApproval: Boolean,
        val itemCount: Int,
        val monetaryValuationComplete: Boolean
    )
    data class VendorBalance(
        val vendorId: String, val billedPaise: Long, val paidPaise: Long,
        val duePaise: Long, val unusedAdvancesPaise: Long
    )
    data class DepartmentLine(
        val name: String, val revenuePaise: Long?, val billedExpensePaise: Long,
        val indicativeDifferencePaise: Long?
    )
    data class Report(
        val month: String,
        val billsPaise: Long, val paidPaise: Long, val duePaise: Long,
        val unusedAdvancesPaise: Long,
        val trackedTaxPaise: Long, val taxPaidPaise: Long, val taxDuePaise: Long,
        val rcmDuePaise: Long, val landlordGstDuePaise: Long,
        val issuedPayrollPaise: Long, val payrollDuePaise: Long,
        val gstBillsYes: Int, val gstBillsNo: Int, val gstBillsUnrecorded: Int,
        val stock: StockState?,
        val vendorBalances: List<VendorBalance>,
        val departments: List<DepartmentLine>,
        val issues: List<Issue>,
        val fingerprint: String
    ) {
        val reconciliationPass: Boolean get() = issues.none { it.severity == Severity.BLOCKER }
        // Review, stock valuation, CA sign-off, and source-revenue certification are prerequisites.
        val readyForFinalClose: Boolean get() = false
    }

    private fun plus(a: Long, b: Long) = Math.addExact(a, b)
    private fun total(values: Iterable<Long>): Long = values.fold(0L, ::plus)
    private fun due(amount: Long, paid: Long): Long = Math.subtractExact(amount, paid)
    private fun group(value: String): String = when {
        value.contains("kitchen", true) || value.contains("f&b", true) ||
            value.contains("food", true) -> "Kitchen"
        value.contains("room", true) || value.contains("front office", true) -> "Rooms"
        value.contains("service", true) || value.contains("bonfire", true) ||
            value.contains("pool", true) -> "Services"
        else -> "Unallocated"
    }

    fun calculate(
        month: String,
        allBills: List<Bill>,
        payments: List<Payment>,
        allRevenue: List<Revenue>,
        taxes: List<LeaseTax>,
        issuedPayroll: List<PayrollRun>,
        stock: StockState?,
        gstBillsYes: Int,
        gstBillsNo: Int,
        gstBillsUnrecorded: Int
    ): Report {
        val ym = YearMonth.parse(month)
        require(!ym.isAfter(YearMonth.now())) { "Future month cannot be reported" }
        require(gstBillsYes >= 0 && gstBillsNo >= 0 && gstBillsUnrecorded >= 0)
        val issues = mutableListOf<Issue>()
        val bills = allBills.filter { it.month == month }
        val activeTaxes = taxes.filter { it.month == month }
        val revenues = allRevenue.filter { it.date.take(7) == month }
        val activePayroll = issuedPayroll.filter { it.month == month }
        val monthPayments = payments.filter { it.date.take(7) == month }
        val exactBillIds = mutableSetOf<String>()
        bills.forEach { b ->
            if (!exactBillIds.add(b.id)) issues += Issue(Severity.BLOCKER, "DUPLICATE_BILL", "Duplicate bill ID: ${b.id}")
            if (b.amountPaise <= 0L || b.paidPaise < 0L || b.duePaise < 0L ||
                runCatching { plus(b.paidPaise, b.duePaise) }.getOrNull() != b.amountPaise) {
                issues += Issue(Severity.BLOCKER, "BILL_BALANCE", "Bill ${b.id} fails amount = paid + due")
            }
            if (b.vendorId.isBlank()) issues += Issue(Severity.BLOCKER, "VENDOR_MISSING", "Bill ${b.id} has no vendor reference")
            if (b.stockPending) issues += Issue(Severity.BLOCKER, "STOCK_PENDING", "Stock receipt pending for bill ${b.id}")
        }
        val ids = mutableSetOf<String>()
        payments.forEach { p ->
            if (!ids.add(p.id)) issues += Issue(Severity.BLOCKER, "DUPLICATE_PAYMENT", "Duplicate payment ID: ${p.id}")
            if (p.amountPaise <= 0L || p.allocatedPaise < 0L || p.allocatedPaise > p.amountPaise)
                issues += Issue(Severity.BLOCKER, "PAYMENT_ALLOCATION", "Payment ${p.id} has invalid allocation")
            if (runCatching { LocalDate.parse(p.date) }.isFailure)
                issues += Issue(Severity.BLOCKER, "INVALID_PAYMENT_DATE", "Invalid payment date for ${p.id}")
        }
        val taxIds = mutableSetOf<String>()
        activeTaxes.forEach { t ->
            if (!taxIds.add(t.id) || t.amountPaise < 0L || t.paidPaise < 0L || t.paidPaise > t.amountPaise)
                issues += Issue(Severity.BLOCKER, "TAX_BALANCE", "Invalid or duplicated rent tax ${t.id}")
            if (t.mode !in setOf("RCM", "LANDLORD_GST", "NONE"))
                issues += Issue(Severity.BLOCKER, "TAX_MODE", "Unrecognized tax treatment on ${t.id}")
        }
        val allBillIds = allBills.map { it.id }.toSet()
        activePayroll.forEach { run ->
            val bill = allBills.firstOrNull { it.id == run.id && it.source == "PAYROLL" }
            if (bill == null || bill.amountPaise != run.amountPaise)
                issues += Issue(Severity.BLOCKER, "PAYROLL_NOT_RECONCILED", "Issued payroll ${run.id} does not reconcile to one payable bill")
        }
        if (allBillIds.size != allBills.size)
            issues += Issue(Severity.BLOCKER, "DUPLICATE_SOURCE", "Source bill IDs are not unique across periods")
        if (stock == null || !stock.approved) issues += Issue(Severity.REVIEW, "STOCK_NOT_CLOSED", "This month has no approved stock closing")
        if (stock != null && stock.month != month) issues += Issue(Severity.BLOCKER, "STOCK_MONTH", "Stock month does not match selected period")
        if (stock?.receiptChangedAfterApproval == true)
            issues += Issue(Severity.BLOCKER, "STOCK_CHANGED", "Receipts changed since stock approval")
        if (stock?.monetaryValuationComplete != true)
            issues += Issue(Severity.REVIEW, "STOCK_UNVALUED", "Quantity closing is not a monetary stock valuation")
        if (revenues.isEmpty()) issues += Issue(Severity.REVIEW, "REVENUE_MISSING", "No verified revenue has been imported for this period")
        issues += Issue(Severity.REVIEW, "REVENUE_SCOPE", "Live PMS completeness and property attribution have not been certified")
        issues += Issue(Severity.REVIEW, "TAX_SCOPE", "GST invoice declarations are not ITC, TDS or TCS returns; CA verification required")
        if (gstBillsUnrecorded > 0)
            issues += Issue(Severity.REVIEW, "GST_BILL_UNRECORDED", "$gstBillsUnrecorded manual expense invoice declarations missing")
        val billsAmount = total(bills.map { it.amountPaise })
        val billPaid = total(bills.map { it.paidPaise })
        val billDue = total(bills.map { it.duePaise })
        val trackedTax = total(activeTaxes.map { it.amountPaise })
        val taxPaid = total(activeTaxes.map { it.paidPaise })
        val taxDue = total(activeTaxes.map { due(it.amountPaise, it.paidPaise) })
        val rcmDue = total(activeTaxes.filter { it.mode == "RCM" }.map { due(it.amountPaise, it.paidPaise) })
        val landlordDue = total(activeTaxes.filter { it.mode == "LANDLORD_GST" }.map { due(it.amountPaise, it.paidPaise) })
        val unusedAdvances = total(payments.filter { it.kind == "ADVANCE" }
            .map { due(it.amountPaise, it.allocatedPaise) })
        val vendorBills = bills.groupBy { it.vendorId }
        val advanceByVendor = payments.filter { it.kind == "ADVANCE" }
            .groupBy { it.vendorId }
        val vendorBalances = (vendorBills.keys + advanceByVendor.keys).distinct().sorted().map { id ->
            val bb = vendorBills[id].orEmpty()
            VendorBalance(id, total(bb.map { it.amountPaise }), total(bb.map { it.paidPaise }),
                total(bb.map { it.duePaise }), total(advanceByVendor[id].orEmpty()
                    .map { due(it.amountPaise, it.allocatedPaise) }))
        }
        val departments = listOf("Rooms", "Kitchen", "Services", "Unallocated").map { name ->
            val rev = revenues.filter { group(it.department) == name }
            val r: Long? = if (rev.isEmpty()) null else total(rev.map { it.amountPaise })
            val expense = total(bills.filter { group(it.department) == name }.map { it.amountPaise })
            DepartmentLine(name, r, expense, r?.let { Math.subtractExact(it, expense) })
        }
        val payrollAmount = total(activePayroll.map { it.amountPaise })
        val payrollDue = total(bills.filter { it.source == "PAYROLL" }.map { it.duePaise })
        // Financial fingerprint is a local drift detector only, NOT a tamper-proof audit signature.
        val canonical = buildString {
            append(month).append('|')
            bills.sortedBy { it.id }.forEach { append("B:").append(it).append('|') }
            monthPayments.sortedBy { it.id }.forEach { append("P:").append(it).append('|') }
            revenues.sortedWith(compareBy<Revenue> { it.date }.thenBy { it.department }).forEach {
                append("R:").append(it).append('|')
            }
            activeTaxes.sortedBy { it.id }.forEach { append("T:").append(it).append('|') }
            activePayroll.sortedBy { it.id }.forEach { append("Y:").append(it).append('|') }
            append("S:").append(stock).append("|G:")
                .append(gstBillsYes).append('/').append(gstBillsNo).append('/').append(gstBillsUnrecorded)
        }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return Report(month, billsAmount, billPaid, billDue, unusedAdvances,
            trackedTax, taxPaid, taxDue, rcmDue, landlordDue,
            payrollAmount, payrollDue, gstBillsYes, gstBillsNo, gstBillsUnrecorded,
            stock, vendorBalances, departments, issues.distinct(), fingerprint)
    }
}

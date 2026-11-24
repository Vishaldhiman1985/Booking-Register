package com.example.bookingregister.finance.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/** V24 read-only exception register. NOT a journal, independent audit or statutory certification. */
object FinanceV24AuditPolicy {
    enum class Severity { BLOCKER, REVIEW }
    data class Issue(val severity: Severity, val code: String, val detail: String)
    data class Bill(val id: String, val month: String, val source: String, val department: String,
                    val grossPaise: Long, val settledPaise: Long, val duePaise: Long,
                    val inventoryPurchase: Boolean)
    data class IssuedSalary(val id: String, val month: String, val grossPaise: Long)
    data class TaxEvidence(val id: String, val month: String, val kind: String,
                           val grossPaise: Long, val settledPaise: Long, val cancelled: Boolean)
    data class RentTax(val id: String, val month: String, val mode: String,
                       val grossPaise: Long, val settledPaise: Long)
    data class Revenue(val date: String, val department: String, val amountPaise: Long)
    data class Stock(val approved: Boolean, val receiptsChanged: Boolean,
                     val itemCount: Int, val pricedItemCount: Int, val consumptionPaise: Long?)
    /** required=true when a department cannot be unambiguously inferred from the source. */
    data class Allocation(val key: String, val amountPaise: Long,
                          val recordedAmountPaise: Long?, val required: Boolean)
    data class Input(val month: String, val coreFingerprint: String,
                     val coreIssues: List<Issue>, val bills: List<Bill>,
                     val salaryRuns: List<IssuedSalary>, val evidence: List<TaxEvidence>,
                     val rentTaxes: List<RentTax>, val revenue: List<Revenue>,
                     val stock: Stock?, val allocations: List<Allocation>,
                     val orphanAllocationKeys: List<String>, val provisionalLock: Boolean,
                     val sourceSignatures: List<String> = emptyList())
    data class Report(val month: String, val fingerprint: String, val issues: List<Issue>,
                      val billGrossPaise: Long, val billDuePaise: Long,
                      val taxEvidencePayablePaise: Long, val taxEvidenceOpenPaise: Long,
                      val potentialTaxCreditsPaise: Long, val rentTaxOpenPaise: Long,
                      val salaryGrossPaise: Long, val stockConsumedPaise: Long?,
                      val revenueEvidencePaise: Long, val provisionalLock: Boolean) {
        val blockerCount: Int get() = issues.count { it.severity == Severity.BLOCKER }
        val reviewCount: Int get() = issues.count { it.severity == Severity.REVIEW }
        /** Always false: live PMS coverage, legal taxes and property mapping are still unverified. */
        val readyForFinalClose: Boolean get() = false
    }

    private fun total(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)
    private fun fingerprint(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Capture a deterministic local drift snapshot. Not a tamper-proof audit signature. */
    fun calculate(input: Input): Report {
        val month = YearMonth.parse(input.month).toString()
        require(month == input.month) { "Noncanonical accounting period" }
        require(Regex("[a-fA-F0-9]{64}").matches(input.coreFingerprint)) { "Base reconciliation fingerprint missing" }
        val issues = input.coreIssues.toMutableList()
        fun issue(severity: Severity, code: String, detail: String) { issues.add(Issue(severity, code, detail)) }
        fun unique(ids: List<String>, name: String) {
            if (ids.any { it.isBlank() } || ids.toSet().size != ids.size)
                issue(Severity.BLOCKER, "DUPLICATE_${name}", "$name has duplicate or blank source identifiers")
        }
        unique(input.bills.map { it.id }, "BILLS")
        unique(input.salaryRuns.map { it.id }, "SALARY")
        unique(input.evidence.map { it.id }, "TAX_EVIDENCE")
        unique(input.rentTaxes.map { it.id }, "RENT_TAX")
        unique(input.allocations.map { it.key }, "ALLOCATIONS")
        if (input.bills.any { it.month != month } || input.salaryRuns.any { it.month != month } ||
            input.evidence.any { it.month != month } || input.rentTaxes.any { it.month != month } ||
            input.revenue.any { runCatching { YearMonth.from(LocalDate.parse(it.date)).toString() }.getOrNull() != month })
            issue(Severity.BLOCKER, "PERIOD_MISMATCH", "Some source rows belong to another month or use invalid dates")
        input.bills.forEach { bill ->
            if (bill.grossPaise <= 0L || bill.settledPaise < 0L || bill.duePaise < 0L ||
                runCatching { Math.addExact(bill.settledPaise, bill.duePaise) }.getOrNull() != bill.grossPaise)
                issue(Severity.BLOCKER, "BILL_UNBALANCED", "Bill ${bill.id} fails gross = paid + outstanding")
            if (bill.department.isBlank()) issue(Severity.REVIEW,"BILL_DEPARTMENT", "Bill ${bill.id}: department missing")
        }
        val byBillId = input.bills.associateBy { it.id }
        input.salaryRuns.forEach { run ->
            val bill = byBillId[run.id]
            if (run.grossPaise < 0L || bill?.source != "PAYROLL" || bill.grossPaise != run.grossPaise)
                issue(Severity.BLOCKER, "ISSUED_SALARY_MISMATCH", "Issued salary ${run.id} has no matching payable amount")
        }
        input.evidence.filterNot { it.cancelled }.forEach { tax ->
            val payable = tax.kind in setOf("OUTPUT_GST", "TDS_WITHHELD", "TCS_COLLECTED")
            val credit = tax.kind in setOf("INPUT_GST", "TCS_CREDIT")
            if ((!payable && !credit) || tax.grossPaise <= 0L || tax.settledPaise < 0L ||
                tax.settledPaise > tax.grossPaise || (credit && tax.settledPaise != 0L))
                issue(Severity.BLOCKER, "TAX_EVIDENCE_INVALID", "Tax evidence ${tax.id} is inconsistent")
        }
        input.evidence.filter { it.cancelled && it.settledPaise != 0L }.forEach {
            issue(Severity.BLOCKER,"CANCELLED_TAX_SETTLED", "Cancelled tax evidence ${it.id} retains settlements")
        }
        input.rentTaxes.forEach { rent ->
            if (rent.grossPaise < 0L || rent.settledPaise < 0L || rent.settledPaise > rent.grossPaise ||
                rent.mode !in setOf("RCM", "LANDLORD_GST", "NONE"))
                issue(Severity.BLOCKER,"RENT_TAX_INVALID", "Rent tax ${rent.id} fails amount or tax-mode check")
        }
        input.allocations.forEach { allocation ->
            if (allocation.amountPaise <= 0L) issue(Severity.BLOCKER,"ALLOCATION_AMOUNT", "Invalid source amount: ${allocation.key}")
            if (allocation.recordedAmountPaise != null && allocation.recordedAmountPaise != allocation.amountPaise)
                issue(Severity.BLOCKER,"ALLOCATION_STALE", "Cost allocation ${allocation.key} no longer matches its source amount")
            if (allocation.required && allocation.recordedAmountPaise == null)
                issue(Severity.REVIEW,"ALLOCATION_MISSING", "Allocate ${allocation.key} to a cost centre with supporting basis")
        }
        input.orphanAllocationKeys.distinct().sorted().forEach {
            issue(Severity.REVIEW,"ALLOCATION_ORPHAN", "Allocation $it has no current source; review its history")
        }
        if (input.stock == null || !input.stock.approved)
            issue(Severity.REVIEW,"STOCK_UNAPPROVED","Physical stock month is not approved")
        input.stock?.let { stock ->
            if (stock.receiptsChanged) issue(Severity.BLOCKER,"STOCK_RECEIPTS_CHANGED", "Goods receipts changed since approved stock snapshot")
            if (stock.itemCount < 0 || stock.pricedItemCount < 0 || stock.pricedItemCount > stock.itemCount ||
                (stock.consumptionPaise != null && stock.consumptionPaise < 0))
                issue(Severity.BLOCKER,"STOCK_COUNTS_INVALID", "Stock valuation has inconsistent item counts or values")
            if (stock.pricedItemCount != stock.itemCount || stock.consumptionPaise == null)
                issue(Severity.REVIEW,"STOCK_PRICE_INCOMPLETE", "Stock consumption is not fully supported by monetary evidence")
        }
        if (input.revenue.isEmpty()) issue(Severity.REVIEW,"REVENUE_EMPTY","No locally imported revenue evidence for this period")
        if (input.revenue.any { it.amountPaise == 0L || it.department.isBlank() })
            issue(Severity.BLOCKER,"REVENUE_INVALID", "Imported revenue has zero amounts or unclassified departments")
        if (!input.provisionalLock) issue(Severity.REVIEW,"ENTRY_LOCK_OPEN", "V20 provisional manual-entry lock is not active")
        issue(Severity.REVIEW,"PMS_UNVERIFIED", "Completeness of live Booking Register revenue and OTA adjustments is not certified")
        issue(Severity.REVIEW,"PROPERTY_UNVERIFIED", "This DEV ledger is not a verified multi-property accounting source")
        issue(Severity.REVIEW,"STATUTORY_UNVERIFIED", "GST, TDS, TCS, ITC and expense tax treatment require CA verification")
        val sorted = issues.distinct().sortedWith(compareBy<Issue> { it.severity.ordinal }.thenBy { it.code }.thenBy { it.detail })
        fun <T> appendRows(tag: String, rows: List<T>, builder: StringBuilder) {
            rows.map { it.toString() }.sorted().forEach { builder.append(tag).append('|').append(it).append('\n') }
        }
        val canonical = StringBuilder().append("V24-AUDIT-SCHEMA1|" ).append(month).append('|')
            .append(input.coreFingerprint.lowercase()).append('|').append(input.provisionalLock).append('\n')
        appendRows("BILL", input.bills, canonical)
        appendRows("SALARY", input.salaryRuns, canonical)
        appendRows("EVIDENCE", input.evidence, canonical)
        appendRows("RENT", input.rentTaxes, canonical)
        appendRows("REVENUE", input.revenue, canonical)
        appendRows("ALLOC", input.allocations, canonical)
        appendRows("ORPHAN", input.orphanAllocationKeys, canonical)
        canonical.append("STOCK|").append(input.stock).append('\n')
        appendRows("ISSUE", sorted, canonical)
        appendRows("SOURCE_SIGNATURE", input.sourceSignatures, canonical)
        val activeTax = input.evidence.filterNot { it.cancelled }
        val payableKinds = setOf("OUTPUT_GST", "TDS_WITHHELD", "TCS_COLLECTED")
        val creditKinds = setOf("INPUT_GST", "TCS_CREDIT")
        return Report(month, fingerprint(canonical.toString()), sorted,
            total(input.bills.map { it.grossPaise }), total(input.bills.map { it.duePaise }),
            total(activeTax.filter { it.kind in payableKinds }.map { it.grossPaise }),
            total(activeTax.filter { it.kind in payableKinds }.map { Math.subtractExact(it.grossPaise,it.settledPaise) }),
            total(activeTax.filter { it.kind in creditKinds }.map { it.grossPaise }),
            total(input.rentTaxes.map { Math.subtractExact(it.grossPaise,it.settledPaise) }),
            total(input.salaryRuns.map { it.grossPaise }), input.stock?.consumptionPaise,
            total(input.revenue.map { it.amountPaise }), input.provisionalLock)
    }
}

package com.example.bookingregister.finance.domain

/** Payroll is already expensed through issued runs. This merely verifies matching payable projections. */
object FinanceV22PayrollAuditPolicy {
    data class Issued(val id: String, val amountPaise: Long)
    data class Payable(val id: String, val amountPaise: Long, val paidPaise: Long, val duePaise: Long)
    data class Check(val issuedPaise: Long, val paidPaise: Long, val duePaise: Long,
                     val issues: List<String>, val issuedCount: Int) {
        val reconciled: Boolean get() = issues.isEmpty()
    }
    fun reconcile(issued: List<Issued>, bills: List<Payable>): Check {
        val issues = mutableListOf<String>()
        if (issued.any { it.id.isBlank() || it.amountPaise < 0 }) issues.add("Invalid issued salary run")
        if (bills.any { it.id.isBlank() || it.amountPaise < 0 || it.paidPaise < 0 || it.duePaise < 0 }) issues.add("Invalid salary bill")
        if (issued.map { it.id }.distinct().size != issued.size) issues.add("Duplicate issued salary run ID")
        if (bills.map { it.id }.distinct().size != bills.size) issues.add("Duplicate salary payable ID")
        val byId = bills.associateBy { it.id }
        issued.forEach { run ->
            val b = byId[run.id]
            if (b == null) issues.add("Salary run missing from payable ledger: ${run.id}")
            else {
                if (run.amountPaise != b.amountPaise) issues.add("Salary payable amount differs: ${run.id}")
                if (run.amountPaise >= 0 && b.paidPaise >= 0 && b.duePaise >= 0 &&
                    Math.addExact(b.paidPaise, b.duePaise) != b.amountPaise)
                    issues.add("Salary settlement does not balance: ${run.id}")
            }
        }
        val runIds = issued.map { it.id }.toSet()
        bills.filter { it.id !in runIds }.forEach { issues.add("Unmatched salary payable: ${it.id}") }
        fun sum(values: List<Long>) = values.fold(0L, Math::addExact)
        return Check(sum(issued.map { it.amountPaise }),sum(bills.map { it.paidPaise }),
            sum(bills.map { it.duePaise }),issues,issued.size)
    }
}

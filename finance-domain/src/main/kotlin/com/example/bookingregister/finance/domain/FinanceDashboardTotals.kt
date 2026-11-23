package com.example.bookingregister.finance.domain

/** A read-only projection of Finance DEV's locally recorded records, not a posted P&L. */
object FinanceDashboardTotals {
    data class Bill(val period: String, val amountPaise: Long, val unpaidPaise: Long, val department: String)
    data class Revenue(val period: String, val amountPaise: Long, val department: String)
    data class LeaseTax(val period: String, val mode: String, val unpaidPaise: Long)
    data class Summary(
        val billAmountPaise: Long,
        val billUnpaidPaise: Long,
        val kitchenBillAmountPaise: Long,
        val importedRevenuePaise: Long?,
        val kitchenRevenuePaise: Long?,
        val indicativeDifferencePaise: Long?,
        val kitchenDifferencePaise: Long?,
        val rcmUnpaidPaise: Long,
        val landlordGstUnpaidPaise: Long,
        val billCount: Int,
        val revenueEntryCount: Int
    ) {
        val taxUnpaidPaise: Long get() = Math.addExact(rcmUnpaidPaise, landlordGstUnpaidPaise)
        val totalOutstandingPaise: Long get() = Math.addExact(billUnpaidPaise, taxUnpaidPaise)
    }

    private fun sum(items: Iterable<Long>): Long = items.fold(0L) { total, value -> Math.addExact(total, value) }
    private fun kitchen(department: String): Boolean {
        val label = department.lowercase()
        return label.contains("kitchen") || label.contains("f&b") || label.contains("food & beverage")
    }

    /** Revenue is unknown unless local imported revenue entries exist for the selected period.
     * Even when known, the result is indicative, not audited profitability.
     * Lease tax is a payable-only projection and is never added to bill-based expenses.
     */
    fun calculate(month: String, allBills: List<Bill>, allRevenue: List<Revenue>, allTax: List<LeaseTax>,
                  allTime: Boolean = false): Summary {
        require(Regex("\\d{4}-\\d{2}").matches(month)) { "Month must be YYYY-MM" }
        val bills = allBills.filter { allTime || it.period == month }
        bills.forEach {
            require(it.amountPaise >= 0L && it.unpaidPaise in 0..it.amountPaise) { "Invalid bill balance" }
        }
        val revenue = allRevenue.filter { allTime || it.period == month }
        val taxes = allTax.filter { allTime || it.period == month }
        taxes.forEach { require(it.unpaidPaise >= 0L) { "Invalid tax balance" } }

        val billed = sum(bills.map { it.amountPaise })
        val unpaid = sum(bills.map { it.unpaidPaise })
        val kitchenBilled = sum(bills.filter { kitchen(it.department) }.map { it.amountPaise })
        val importedRevenue = revenue.takeIf { it.isNotEmpty() }?.let { sum(it.map(Revenue::amountPaise)) }
        val kitchenRevenue = revenue.filter { kitchen(it.department) }
            .takeIf { it.isNotEmpty() }?.let { sum(it.map(Revenue::amountPaise)) }
        return Summary(
            billAmountPaise = billed,
            billUnpaidPaise = unpaid,
            kitchenBillAmountPaise = kitchenBilled,
            importedRevenuePaise = importedRevenue,
            kitchenRevenuePaise = kitchenRevenue,
            indicativeDifferencePaise = importedRevenue?.let { Math.subtractExact(it, billed) },
            kitchenDifferencePaise = kitchenRevenue?.let { Math.subtractExact(it, kitchenBilled) },
            rcmUnpaidPaise = sum(taxes.filter { it.mode == "RCM" }.map { it.unpaidPaise }),
            landlordGstUnpaidPaise = sum(taxes.filter { it.mode == "LANDLORD_GST" }.map { it.unpaidPaise }),
            billCount = bills.size,
            revenueEntryCount = revenue.size
        )
    }
}

package com.example.bookingregister.finance.dev

/** Pure, offline-only allocation preview. No posting or bank transfer authority. */
internal object FinanceDemoPaymentPolicy {
    data class OpenBill(val id: String, val invoiceDate: String, val outstandingPaise: Long)
    data class Allocation(val billId: String, val appliedPaise: Long)
    data class Preview(val allocations: List<Allocation>, val unallocatedPaise: Long)

    fun oldestFirst(bills: List<OpenBill>, paymentPaise: Long): Preview {
        require(paymentPaise > 0) { "Payment must be positive" }
        require(bills.map { it.id }.distinct().size == bills.size) { "Duplicate bill" }
        require(bills.all { it.outstandingPaise >= 0 }) { "Negative outstanding balance" }
        var remaining = paymentPaise
        val allocations = bills.sortedWith(compareBy<OpenBill> { it.invoiceDate }.thenBy { it.id })
            .mapNotNull { bill ->
                val applied = minOf(remaining, bill.outstandingPaise)
                remaining -= applied
                if (applied > 0L) Allocation(bill.id, applied) else null
            }
        return Preview(allocations, remaining)
    }
}

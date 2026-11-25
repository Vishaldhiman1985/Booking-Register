package com.example.bookingregister.finance.dev

import java.time.LocalDate

/** Pure, deterministic oldest-first payment planning; amounts are always in paise. */
internal object FinancePayeeAllocationPolicy {
    data class OpenBill(val id: String, val billDate: String, val dueDate: String, val duePaise: Long)
    data class Allocation(val billId: String, val amountPaise: Long)

    fun plan(bills: List<OpenBill>, requestedPaise: Long): List<Allocation> {
        require(requestedPaise > 0L) { "Enter a positive payment amount" }
        val eligible = bills.filter { it.duePaise > 0L }.sortedWith(
            compareBy<OpenBill> { LocalDate.parse(it.billDate) }
                .thenBy { LocalDate.parse(it.dueDate) }.thenBy { it.id }
        )
        val outstanding = eligible.fold(0L) { sum, bill -> Math.addExact(sum, bill.duePaise) }
        require(requestedPaise <= outstanding) { "Payment exceeds outstanding dues" }
        var remaining = requestedPaise
        val applied = mutableListOf<Allocation>()
        for (bill in eligible) {
            if (remaining == 0L) break
            val amount = minOf(remaining, bill.duePaise)
            applied.add(Allocation(bill.id, amount))
            remaining = Math.subtractExact(remaining, amount)
        }
        check(remaining == 0L) { "Payment was not fully allocated" }
        return applied
    }
}

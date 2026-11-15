package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceExpenseStockPolicyTest {
    private val rice = HotelPurchaseItem("rice-1", "grocery", "Basmati Rice", "kg")
    private val milk = HotelPurchaseItem("milk-1", "dairy", "Milk", "litre")
    private fun line(id: String = rice.id, name: String = rice.name, unit: String = rice.unit, amount: Double = 2.0) =
        FinanceExpenseStockLine(id, name, unit, amount)

    @Test fun directReceiptRequiresActiveCatalogItemsAllowedGroupsAndMatchingUnits() {
        FinanceExpenseStockPolicy.validateLines(listOf(line()), listOf(rice, milk), setOf("grocery"))
        rejects { FinanceExpenseStockPolicy.validateLines(emptyList(), listOf(rice), setOf("grocery")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line(unit = "piece")), listOf(rice), setOf("grocery")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line(amount = 0.0)), listOf(rice), setOf("grocery")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line(amount = Double.NaN)), listOf(rice), setOf("grocery")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line(), line()), listOf(rice), setOf("grocery")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line()), listOf(rice), setOf("dairy")) }
        rejects { FinanceExpenseStockPolicy.validateLines(listOf(line()), listOf(rice.copy(active = false)), setOf("grocery")) }
    }

    @Test fun closedMonthsAndFutureDatesCannotReceiveNewItems() {
        val today = LocalDate.now()
        FinanceExpenseStockPolicy.requireOpenReceiptMonth(today.toString(), emptyList())
        rejects { FinanceExpenseStockPolicy.requireOpenReceiptMonth(today.plusDays(1).toString(), emptyList()) }
        rejects { FinanceExpenseStockPolicy.requireOpenReceiptMonth(today.toString(), listOf(today.withDayOfMonth(1).toString().take(7))) }
    }

    @Test fun confirmedStockLocksClassificationVendorDateAndAmount() {
        val draft = FinanceDemoDraftPolicy.create(
            FinanceDemoDraftPolicy.DraftInput(
                vendorCode = "no_payee", category = "Food & Beverage Cost",
                invoiceDate = "2026-11-12", amountPaise = 235000,
                paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID,
                amountPaidPaise = 0,
                expenseCategoryCode = "HC02", expenseTypeCode = "HE0201", expenseTypeLabel = "Food Ingredients",
                department = "Kitchen"
            ), "draft-1", 123L
        )
        val link = FinanceExpenseStockLink(draft.draftId, "HE0201", "2026-11", setOf("grocery"),
            FinanceExpenseStockPolicy.RECEIVED_DIRECT, "2026-11-12", null, listOf(line()), 124L)
        FinanceExpenseStockPolicy.requireEditable(draft, draft.copy(notes = "Correct typo"), link)
        rejects { FinanceExpenseStockPolicy.requireEditable(draft, draft.copy(amountPaise = 235001L), link) }
        rejects { FinanceExpenseStockPolicy.requireEditable(draft, draft.copy(vendorCode = "one_time:Other"), link) }
        rejects { FinanceExpenseStockPolicy.requireEditable(draft, draft.copy(department = "Rooms"), link) }
        rejects { FinanceExpenseStockPolicy.requireDiscardable(link) }
        FinanceExpenseStockPolicy.requireDiscardable(link.copy(state = FinanceExpenseStockPolicy.PENDING))
    }

    @Test fun verifiedInvoiceCurrencyIsConvertedToPaisePrecisely() {
        assertEquals(235025L, FinanceExpenseStockPolicy.poAmountInPaise(2350.25))
        assertEquals(100L, FinanceExpenseStockPolicy.poAmountInPaise(1.0))
        rejects { FinanceExpenseStockPolicy.poAmountInPaise(-1.0) }
    }

    private fun rejects(action: () -> Unit) {
        var thrown = false
        try { action() } catch (_: RuntimeException) { thrown = true }
        assertTrue("Expected validation error", thrown)
    }
}

package com.example.bookingregister.finance.domain

import org.junit.Assert.*
import org.junit.Test

class FinanceV24ReconciliationTest {
    private val m = "2026-11"
    private val rent = FinanceV24Reconciliation.Bill("rent", m, "landlord", "FIXED", "Rooms", 100_000_00, 80_000_00, 20_000_00, false)
    private val kitchen = FinanceV24Reconciliation.Bill("grocery", m, "supplier", "MANUAL", "Kitchen", 30_000_00, 0, 30_000_00, false)
    private val salary = FinanceV24Reconciliation.Bill("salary", m, "employee:1", "PAYROLL", "Kitchen", 20_000_00, 5_000_00, 15_000_00, false)
    private fun report(
        bills: List<FinanceV24Reconciliation.Bill> = listOf(rent, kitchen, salary),
        payments: List<FinanceV24Reconciliation.Payment> = emptyList(),
        revenue: List<FinanceV24Reconciliation.Revenue> = emptyList(),
        taxes: List<FinanceV24Reconciliation.LeaseTax> = emptyList(),
        payroll: List<FinanceV24Reconciliation.PayrollRun> = listOf(FinanceV24Reconciliation.PayrollRun("salary", m, 20_000_00))
    ) = FinanceV24Reconciliation.calculate(m, bills, payments, revenue, taxes, payroll, null, 0, 0, 3)

    @Test fun rentTaxIsNotDoubleCountedInExpenses() {
        val r = report(taxes = listOf(FinanceV24Reconciliation.LeaseTax("tax1",m,"RCM",18_000_00,3_000_00)))
        assertEquals(150_000_00L, r.billsPaise)
        assertEquals(85_000_00L, r.paidPaise) // 80k rent plus 5k payroll
        assertEquals(65_000_00L, r.duePaise)
        assertEquals(15_000_00L, r.taxDuePaise)
        assertEquals(15_000_00L, r.rcmDuePaise)
    }

    @Test fun missingRevenueDoesNotProduceFictitiousProfit() {
        val r = report()
        assertNull(r.departments.single { it.name == "Rooms" }.indicativeDifferencePaise)
        assertTrue(r.issues.any { it.code == "REVENUE_MISSING" })
        assertFalse(r.readyForFinalClose)
    }

    @Test fun departmentDifferencesAreOnlyIndicative() {
        val r = report(revenue=listOf(
            FinanceV24Reconciliation.Revenue("2026-11-05", "Rooms", 200_000_00),
            FinanceV24Reconciliation.Revenue("2026-11-06", "Kitchen (F&B)", 75_000_00)))
        assertEquals(100_000_00L,r.departments.single { it.name == "Rooms" }.indicativeDifferencePaise)
        assertEquals(25_000_00L,r.departments.single { it.name == "Kitchen" }.indicativeDifferencePaise)
        assertNull(r.departments.single { it.name == "Services" }.indicativeDifferencePaise)
        assertTrue(r.issues.any { it.code == "STOCK_UNVALUED" })
    }

    @Test fun validatesPaymentAndBillConservation() {
        val bad = rent.copy(paidPaise=110_000_00, duePaise=0)
        val payment = FinanceV24Reconciliation.Payment("p1","landlord",10_000_00,11_000_00,"ADVANCE","2026-11-15","ABC")
        val r = report(bills=listOf(bad),payments=listOf(payment),payroll=emptyList())
        assertTrue(r.issues.any { it.code == "BILL_BALANCE" })
        assertTrue(r.issues.any { it.code == "PAYMENT_ALLOCATION" })
        assertFalse(r.reconciliationPass)
    }

    @Test fun unreconciledSalaryBlocksReview() {
        val r=report(bills=listOf(rent), payroll=listOf(FinanceV24Reconciliation.PayrollRun("missing",m,10_000_00)))
        assertTrue(r.issues.any { it.code == "PAYROLL_NOT_RECONCILED" })
    }

    @Test fun unusedAdvancesDoNotDecreaseExpensesOrDue() {
        val p=FinanceV24Reconciliation.Payment("p1","supplier",10_000_00,2_000_00,"ADVANCE","2026-11-12","U1")
        val r=report(payments=listOf(p))
        assertEquals(8_000_00L,r.unusedAdvancesPaise)
        assertEquals(30_000_00L,r.vendorBalances.single { it.vendorId=="supplier" }.duePaise)
        assertEquals(8_000_00L,r.vendorBalances.single { it.vendorId=="supplier" }.unusedAdvancesPaise)
    }

    @Test fun fingerprintStableAcrossInputOrderAndChangesWhenBillChanges() {
        val a=report()
        val b=report(bills=listOf(salary,kitchen,rent))
        assertEquals(a.fingerprint,b.fingerprint)
        val c=report(bills=listOf(rent.copy(paidPaise=90_000_00,duePaise=10_000_00),kitchen,salary))
        assertNotEquals(a.fingerprint,c.fingerprint)
    }

    @Test fun stockReceiptChangesBlockAndTaxModesKeptSeparate() {
        val stock=FinanceV24Reconciliation.StockState(m,true,true,12,false)
        val r=FinanceV24Reconciliation.calculate(m,listOf(rent),emptyList(),emptyList(),listOf(
            FinanceV24Reconciliation.LeaseTax("g",m,"LANDLORD_GST",18_000_00,0),
            FinanceV24Reconciliation.LeaseTax("r",m,"RCM",9_000_00,0)
        ),emptyList(),stock,1,0,0)
        assertEquals(27_000_00L,r.taxDuePaise)
        assertEquals(18_000_00L,r.landlordGstDuePaise)
        assertEquals(9_000_00L,r.rcmDuePaise)
        assertTrue(r.issues.any { it.code=="STOCK_CHANGED" })
    }
}

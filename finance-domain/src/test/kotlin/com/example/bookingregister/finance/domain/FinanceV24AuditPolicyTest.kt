package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceV24AuditPolicyTest {
    private val p = FinanceV24AuditPolicy
    private val month = "2026-10"
    private fun base() = FinanceV24AuditPolicy.Input(
        month, "a".repeat(64), emptyList(),
        listOf(FinanceV24AuditPolicy.Bill("salary:1", month, "PAYROLL", "Kitchen", 800_00, 300_00, 500_00, false),
            FinanceV24AuditPolicy.Bill("rent:1",month,"FIXED","Rooms",1000_00,1000_00,0,false)),
        listOf(FinanceV24AuditPolicy.IssuedSalary("salary:1",month,800_00)),
        listOf(FinanceV24AuditPolicy.TaxEvidence("tx:1",month,"TDS_WITHHELD",120_00,20_00,false),
            FinanceV24AuditPolicy.TaxEvidence("credit:1",month,"INPUT_GST",30_00,0,false)),
        listOf(FinanceV24AuditPolicy.RentTax("r:1",month,"RCM",180_00,80_00)),
        listOf(FinanceV24AuditPolicy.Revenue("2026-10-15","Rooms",2500_00)),
        FinanceV24AuditPolicy.Stock(true,false,1,1,250_00),
        listOf(FinanceV24AuditPolicy.Allocation("bill:rent:1",1000_00,null,false)),
        emptyList(),true
    )
    private fun has(report: FinanceV24AuditPolicy.Report, code: String) = report.issues.any { it.code == code }

    @Test fun exposesPaiseBalancesWithoutNettingCreditsOrRentTax() {
        val result = p.calculate(base())
        assertEquals(1800_00L,result.billGrossPaise)
        assertEquals(500_00L,result.billDuePaise)
        assertEquals(120_00L,result.taxEvidencePayablePaise)
        assertEquals(100_00L,result.taxEvidenceOpenPaise)
        assertEquals(30_00L,result.potentialTaxCreditsPaise)
        assertEquals(100_00L,result.rentTaxOpenPaise)
        assertEquals(800_00L,result.salaryGrossPaise)
        assertEquals(250_00L,result.stockConsumedPaise)
        assertEquals(2500_00L,result.revenueEvidencePaise)
        assertFalse(result.readyForFinalClose)
    }
    @Test fun sourceOrderingCannotChangeFingerprint() {
        val a=base()
        val b=a.copy(bills=a.bills.reversed(),evidence=a.evidence.reversed())
        assertEquals(p.calculate(a).fingerprint,p.calculate(b).fingerprint)
    }
    @Test fun sourceEvidenceChangesFingerprintWithoutChangingTotals() {
        val a=base().copy(sourceSignatures=listOf("cost:rice:price100", "allocation:rent:rooms100"))
        val b=a.copy(sourceSignatures=listOf("cost:rice:price100", "allocation:rent:kitchen100"))
        assertNotEquals(p.calculate(a).fingerprint,p.calculate(b).fingerprint)
        assertEquals(p.calculate(a).billGrossPaise,p.calculate(b).billGrossPaise)
    }
    @Test fun sourceSignatureOrderingIsStable() {
        val a=base().copy(sourceSignatures=listOf("Z:3", "A:1"))
        assertEquals(p.calculate(a).fingerprint,p.calculate(a.copy(sourceSignatures=a.sourceSignatures.reversed())).fingerprint)
    }
    @Test fun updatedMoneyChangesSnapshot() {
        val a=base();val b=a.copy(revenue=listOf(FinanceV24AuditPolicy.Revenue("2026-10-15","Rooms",2600_00)))
        assertNotEquals(p.calculate(a).fingerprint,p.calculate(b).fingerprint)
    }
    @Test fun duplicateBillIsBlocked() {
        val a=base(); assertTrue(has(p.calculate(a.copy(bills=a.bills+a.bills.first())),"DUPLICATE_BILLS"))
    }
    @Test fun salaryMissingFromPayablesIsBlocked() {
        val a=base();assertTrue(has(p.calculate(a.copy(bills=listOf(a.bills.last()))),"ISSUED_SALARY_MISMATCH"))
    }
    @Test fun invalidBillBalanceIsBlocked() {
        val a=base();val bad=a.bills.first().copy(duePaise=501_00)
        assertTrue(has(p.calculate(a.copy(bills=listOf(bad,a.bills.last()))),"BILL_UNBALANCED"))
    }
    @Test fun costAllocationStaleDetected() {
        val a=base();val row=FinanceV24AuditPolicy.Allocation("bill:rent:1",1000_00,900_00,true)
        assertTrue(has(p.calculate(a.copy(allocations=listOf(row))),"ALLOCATION_STALE"))
    }
    @Test fun missingRequiredAllocationAndOrphanReported() {
        val a=base(); val row=FinanceV24AuditPolicy.Allocation("stock:rice",250_00,null,true)
        val result=p.calculate(a.copy(allocations=listOf(row),orphanAllocationKeys=listOf("bill:old")))
        assertTrue(has(result,"ALLOCATION_MISSING")); assertTrue(has(result,"ALLOCATION_ORPHAN"))
    }
    @Test fun changedReceiptsInvalidateStock() {
        val a=base();assertTrue(has(p.calculate(a.copy(stock=FinanceV24AuditPolicy.Stock(true,true,1,1,250_00))),"STOCK_RECEIPTS_CHANGED"))
    }
    @Test fun incompleteStockPricingNeverInventsCost() {
        val a=base();val result=p.calculate(a.copy(stock=FinanceV24AuditPolicy.Stock(true,false,3,1,null)))
        assertTrue(has(result,"STOCK_PRICE_INCOMPLETE"));assertEquals(null,result.stockConsumedPaise)
    }
    @Test fun potentialTaxCreditsAreNotSettlements() {
        val a=base();val invalid=a.evidence.last().copy(settledPaise=1)
        assertTrue(has(p.calculate(a.copy(evidence=listOf(a.evidence.first(),invalid))),"TAX_EVIDENCE_INVALID"))
    }
    @Test fun cancelledTaxCannotRetainPayments() {
        val a=base();val invalid=a.evidence.first().copy(cancelled=true)
        assertTrue(has(p.calculate(a.copy(evidence=listOf(invalid,a.evidence.last()))),"CANCELLED_TAX_SETTLED"))
    }
    @Test fun aReviewCannotBeMisrepresentedAsFinalClose() {
        val a=base()
        val result=p.calculate(a)
        assertFalse(result.readyForFinalClose)
        assertTrue(has(result,"PMS_UNVERIFIED"))
        assertTrue(has(result,"PROPERTY_UNVERIFIED"))
        assertTrue(has(result,"STATUTORY_UNVERIFIED"))
    }
    @Test fun invalidMonthIsNotAccepted() {
        val a=base()
        var failed=false
        try { p.calculate(a.copy(month="2026-13")) } catch (_:Exception) { failed=true }
        assertTrue(failed)
    }
    @Test fun zeroRevenueShowsMissingEvidence() {
        val a=base()
        assertTrue(has(p.calculate(a.copy(revenue=emptyList())),"REVENUE_EMPTY"))
    }
    @Test fun coreBlockersAreCarriedIntoAuditReport() {
        val a=base()
        val result=p.calculate(a.copy(coreIssues=listOf(FinanceV24AuditPolicy.Issue(FinanceV24AuditPolicy.Severity.BLOCKER,"EXISTING_ERROR","Core mismatch"))))
        assertEquals(1,result.blockerCount)
    }
}

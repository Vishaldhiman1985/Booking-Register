package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceV22PayrollAuditPolicyTest {
    private val p=FinanceV22PayrollAuditPolicy
    @Test fun issuedSalaryMatchesPaidAndOutstandingWithoutDuplicateExpense() {
        val report=p.reconcile(listOf(FinanceV22PayrollAuditPolicy.Issued("payroll:a",100000)),listOf(FinanceV22PayrollAuditPolicy.Payable("payroll:a",100000,25000,75000)))
        assertTrue(report.reconciled)
        assertEquals(100000L,report.issuedPaise)
        assertEquals(25000L,report.paidPaise)
        assertEquals(75000L,report.duePaise)
    }
    @Test fun missingIssuedSalaryBillBlocksReconciliation() {
        assertFalse(p.reconcile(listOf(FinanceV22PayrollAuditPolicy.Issued("one",100)),emptyList()).reconciled)
    }
    @Test fun changedAmountAndUnmatchedBillBlockReconciliation() {
        val report=p.reconcile(listOf(FinanceV22PayrollAuditPolicy.Issued("one",100)),listOf(FinanceV22PayrollAuditPolicy.Payable("one",90,40,50),FinanceV22PayrollAuditPolicy.Payable("other",10,0,10)))
        assertFalse(report.reconciled)
        assertTrue(report.issues.any{it.contains("differs")})
        assertTrue(report.issues.any{it.contains("Unmatched")})
    }
    @Test fun brokenSettlementAndDuplicateRunIdsBlockReconciliation() {
        val report=p.reconcile(listOf(FinanceV22PayrollAuditPolicy.Issued("one",100),FinanceV22PayrollAuditPolicy.Issued("one",100)),listOf(FinanceV22PayrollAuditPolicy.Payable("one",100,30,50)))
        assertFalse(report.reconciled)
        assertTrue(report.issues.any{it.contains("Duplicate")})
        assertTrue(report.issues.any{it.contains("balance")})
    }
}

package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDemoAccountingRulePolicyTest {
    private fun proposal() = FinanceDemoAccountingRuleProposal(
        proposalId = "test-proposal",
        typeCode = "HE1003",
        ruleKind = FinanceDemoAccountingRulePolicy.LEDGER,
        revisionNumber = 0L,
        previousMappingHint = "Other expenses",
        proposedValue = "Communication Charges",
        proposedGroup = "Other Expenses",
        effectiveFrom = "2027-04-01",
        sourceReference = "CA working paper WP-2027-04",
        reason = "Reviewed proposed reporting change",
        status = FinanceDemoAccountingRulePolicy.PENDING,
        submittedAtMillis = 1000L
    )

    @Test fun acceptsPendingEffectiveDatedProposal() {
        FinanceDemoAccountingRulePolicy.validate(proposal())
        assertEquals("HE1003", proposal().typeCode)
    }

    @Test fun rejectsUnverifiedApprovalOrInvalidEffectiveDate() {
        assertFails { FinanceDemoAccountingRulePolicy.validate(proposal().copy(status = "APPROVED")) }
        assertFails { FinanceDemoAccountingRulePolicy.validate(proposal().copy(effectiveFrom = "2027-02-30")) }
    }

    @Test fun rejectsMissingSourceOrReasonOrUnrecognizedCode() {
        assertFails { FinanceDemoAccountingRulePolicy.validate(proposal().copy(sourceReference = "")) }
        assertFails { FinanceDemoAccountingRulePolicy.validate(proposal().copy(reason = "")) }
        assertFails { FinanceDemoAccountingRulePolicy.validate(proposal().copy(typeCode = "HEXXXX")) }
    }

    @Test fun taxPolicyNoteCannotApplyWithoutApproval() {
        FinanceDemoAccountingRulePolicy.validate(proposal().copy(
            ruleKind = FinanceDemoAccountingRulePolicy.TAX,
            proposedValue = "Verify GST treatment against official notification",
            proposedGroup = "GST"
        ))
        assertEquals("PENDING_CA_REVIEW", FinanceDemoAccountingRulePolicy.PENDING)
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected rejection", failed)
    }
}

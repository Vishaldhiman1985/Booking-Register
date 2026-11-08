package com.example.bookingregister.finance.dev

import java.time.LocalDate

/** Pure validation. No mapping proposals are applied to drafts or posted to books. */
internal object FinanceDemoAccountingRulePolicy {
    const val PENDING = "PENDING_CA_REVIEW"
    const val LEDGER = "LEDGER_MAPPING"
    const val TAX = "TAX_POLICY_NOTE"

    fun validate(proposal: FinanceDemoAccountingRuleProposal) {
        require(proposal.proposalId.isNotBlank() && proposal.submittedAtMillis > 0L)
        require(Regex("HE[0-9]{4}").matches(proposal.typeCode)) { "Invalid expense type code" }
        require(proposal.ruleKind == LEDGER || proposal.ruleKind == TAX) { "Invalid rule kind" }
        require(proposal.status == PENDING) { "Finance DEV cannot approve rules" }
        require(proposal.revisionNumber >= 0L)
        require(proposal.previousMappingHint.length <= 240)
        require(proposal.proposedValue.trim().length in 3..160) { "Enter a proposed ledger or rule note" }
        require(proposal.proposedGroup.trim().length in 2..100) { "Enter the proposed group" }
        val date = runCatching { LocalDate.parse(proposal.effectiveFrom) }.getOrNull()
        require(date != null && date.year in 2020..2100) { "Enter a valid effective date" }
        require(proposal.sourceReference.trim().length in 5..240) { "Add a verifiable source reference" }
        require(proposal.reason.trim().length in 8..240) { "Explain why the rule should change" }
    }
}

package com.bookingregister.finance.domain

import java.time.Instant
import java.time.YearMonth
import java.util.Collections

enum class EntrySide { DEBIT, CREDIT }

data class JournalLine(
    val lineId: String,
    val accountId: String,
    val side: EntrySide,
    val amount: Money,
    val memo: String? = null
) {
    init {
        require(lineId.isNotBlank() && accountId.isNotBlank()) { "Line and account IDs are required" }
        require(amount.minorUnits > 0L) { "Journal line amount must be positive" }
    }
}

/** A client-side proposal. It is NOT a posted journal and grants no posting authority. */
class JournalProposal(
    val commandId: String,
    val sourceId: String,
    val sourceRevision: Long,
    val scope: FinanceScope,
    val accountingPeriod: YearMonth,
    val currency: CurrencyCode,
    val actorId: String,
    lines: List<JournalLine>,
    val reversalOfJournalId: String? = null,
    val correctionReason: String? = null
) {
    val lines: List<JournalLine> = Collections.unmodifiableList(ArrayList(lines))

    init {
        require(commandId.isNotBlank() && sourceId.isNotBlank() && actorId.isNotBlank()) {
            "Command, source and actor IDs are mandatory"
        }
        require(sourceRevision >= 0L) { "Source revision must be nonnegative" }
        if (reversalOfJournalId != null) {
            require(reversalOfJournalId.isNotBlank()) { "Reversed journal id cannot be blank" }
            require(!correctionReason.isNullOrBlank()) { "A correction reason is mandatory" }
        }
    }
}

data class JournalBalance(val debit: Money, val credit: Money)

object JournalValidator {
    /** All validation runs before a command is submitted; server must repeat it atomically. */
    fun validate(proposal: JournalProposal, chart: ChartOfAccounts): JournalBalance {
        require(chart.scope == proposal.scope) { "Wrong tenant, hotel or property scope" }
        require(chart.currency == proposal.currency) { "Wrong chart currency" }
        require(proposal.lines.size >= 2) { "Journal needs at least two lines" }
        require(proposal.lines.map { it.lineId }.distinct().size == proposal.lines.size) {
            "Duplicate journal line id"
        }
        var debits = Money.zero(proposal.currency)
        var credits = Money.zero(proposal.currency)
        for (line in proposal.lines) {
            require(line.amount.currency == proposal.currency) { "Journal contains mixed currencies" }
            val account = chart.findAccount(line.accountId)
                ?: throw IllegalArgumentException("Unknown account: ${line.accountId}")
            require(account.active) { "Inactive account: ${line.accountId}" }
            when (line.side) {
                EntrySide.DEBIT -> debits += line.amount
                EntrySide.CREDIT -> credits += line.amount
            }
        }
        require(debits.minorUnits > 0L && debits == credits) { "Journal is not debit/credit balanced" }
        return JournalBalance(debits, credits)
    }
}

/** Read-only representation of a server-confirmed journal; this class does not post anything. */
class PostedJournalSnapshot(
    val serverJournalId: String,
    val serverPostedAt: Instant,
    val proposal: JournalProposal
) {
    init {
        require(serverJournalId.isNotBlank()) { "Server journal id is required" }
    }
}

/** Reverses existing sides, never deletes or edits the original posted journal. */
object ReversalPlanner {
    fun propose(
        original: PostedJournalSnapshot,
        commandId: String,
        sourceId: String,
        sourceRevision: Long,
        accountingPeriod: YearMonth,
        actorId: String,
        reason: String
    ): JournalProposal {
        require(reason.isNotBlank()) { "Mandatory correction reason is missing" }
        require(commandId != original.proposal.commandId) { "Reversal requires a new command ID" }
        val swapped = original.proposal.lines.map { line ->
            line.copy(
                lineId = "reversal-${line.lineId}",
                side = if (line.side == EntrySide.DEBIT) EntrySide.CREDIT else EntrySide.DEBIT
            )
        }
        return JournalProposal(
            commandId = commandId,
            sourceId = sourceId,
            sourceRevision = sourceRevision,
            scope = original.proposal.scope,
            accountingPeriod = accountingPeriod,
            currency = original.proposal.currency,
            actorId = actorId,
            lines = swapped,
            reversalOfJournalId = original.serverJournalId,
            correctionReason = reason
        )
    }
}

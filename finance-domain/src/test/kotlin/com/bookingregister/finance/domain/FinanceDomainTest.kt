package com.bookingregister.finance.domain

import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDomainTest {
    private val scope = FinanceScope(FinanceEnvironment.FINANCE_DEV, "tenant-a", "hotel-a", "property-a")
    private val inr = CurrencyCode.INR
    private val rent = LedgerAccount("rent", "5000", "Rent", scope, inr, AccountCategory.EXPENSE, NormalBalance.DEBIT)
    private val gst = LedgerAccount("gst", "1200", "Input GST", scope, inr, AccountCategory.ASSET, NormalBalance.DEBIT)
    private val payables = LedgerAccount("payable", "2100", "Vendor payable", scope, inr, AccountCategory.LIABILITY, NormalBalance.CREDIT)
    private val chart = ChartOfAccounts(scope, inr, listOf(rent, gst, payables))

    private fun proposal(lines: List<JournalLine> = listOf(
        JournalLine("l1", "rent", EntrySide.DEBIT, Money(inr, 25_000_000)),
        JournalLine("l2", "gst", EntrySide.DEBIT, Money(inr, 4_500_000)),
        JournalLine("l3", "payable", EntrySide.CREDIT, Money(inr, 29_500_000))
    )) = JournalProposal(
        commandId = "command-001", sourceId = "bill-001", sourceRevision = 1,
        scope = scope, accountingPeriod = YearMonth.of(2026, 11), currency = inr,
        actorId = "manager-a", lines = lines
    )

    @Test fun moneyUsesIntegerPaiseWithExactAddition() {
        assertEquals(103L, (Money(inr, 100) + Money(inr, 3)).minorUnits)
        assertEquals(97L, (Money(inr, 100) - Money(inr, 3)).minorUnits)
    }

    @Test fun moneyOverflowFailsInsteadOfWrapping() {
        assertThrows(ArithmeticException::class.java) { Money(inr, Long.MAX_VALUE) + Money(inr, 1) }
        assertThrows(ArithmeticException::class.java) { Money(inr, Long.MIN_VALUE).negate() }
    }

    @Test fun gstAtEighteenPercentUsesExactIntegerArithmetic() {
        assertEquals(4_500_000L, BasisPoints(1_800).of(Money(inr, 25_000_000)).minorUnits)
    }

    @Test fun roundingIsExplicitAndDeterministicIncludingNegativeAmounts() {
        assertEquals(1L, BasisPoints(5_000).of(Money(inr, 1), MinorUnitRounding.HALF_UP).minorUnits)
        assertEquals(0L, BasisPoints(5_000).of(Money(inr, 1), MinorUnitRounding.HALF_EVEN).minorUnits)
        assertEquals(-1L, BasisPoints(5_000).of(Money(inr, -1), MinorUnitRounding.HALF_UP).minorUnits)
    }

    @Test fun invalidRateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BasisPoints(-1) }
        assertThrows(IllegalArgumentException::class.java) { BasisPoints(10_001) }
    }

    @Test fun novemberRentInvoiceBalancesWithoutPayingGst() {
        val balance = JournalValidator.validate(proposal(), chart)
        assertEquals(29_500_000L, balance.debit.minorUnits)
        assertEquals(balance.debit, balance.credit)
    }

    @Test fun unbalancedJournalIsRejected() {
        val wrong = proposal(listOf(
            JournalLine("d", "rent", EntrySide.DEBIT, Money(inr, 100)),
            JournalLine("c", "payable", EntrySide.CREDIT, Money(inr, 99))
        ))
        assertThrows(IllegalArgumentException::class.java) { JournalValidator.validate(wrong, chart) }
    }

    @Test fun journalWithDuplicateLineIdIsRejected() {
        val wrong = proposal(listOf(
            JournalLine("dup", "rent", EntrySide.DEBIT, Money(inr, 100)),
            JournalLine("dup", "payable", EntrySide.CREDIT, Money(inr, 100))
        ))
        assertThrows(IllegalArgumentException::class.java) { JournalValidator.validate(wrong, chart) }
    }

    @Test fun unknownAccountIsRejected() {
        val wrong = proposal(listOf(
            JournalLine("a", "unmapped", EntrySide.DEBIT, Money(inr, 100)),
            JournalLine("b", "payable", EntrySide.CREDIT, Money(inr, 100))
        ))
        assertThrows(IllegalArgumentException::class.java) { JournalValidator.validate(wrong, chart) }
    }

    @Test fun propertyIsolationIsEnforced() {
        val otherScope = scope.copy(propertyId = "property-b")
        val wrongChart = ChartOfAccounts(otherScope, inr, listOf(rent.copy(scope = otherScope)))
        assertThrows(IllegalArgumentException::class.java) { JournalValidator.validate(proposal(), wrongChart) }
    }

    @Test fun inactiveAccountIsRejected() {
        val inactiveChart = ChartOfAccounts(scope, inr, listOf(rent.copy(active = false), gst, payables))
        assertThrows(IllegalArgumentException::class.java) { JournalValidator.validate(proposal(), inactiveChart) }
    }

    @Test fun accountCatalogueMustBePropertyScoped() {
        assertThrows(IllegalArgumentException::class.java) {
            ChartOfAccounts(scope, inr, listOf(rent.copy(scope = scope.copy(tenantId = "tenant-b"))))
        }
    }

    @Test fun proposalDefensivelyCopiesLines() {
        val original = mutableListOf(
            JournalLine("a", "rent", EntrySide.DEBIT, Money(inr, 100)),
            JournalLine("b", "payable", EntrySide.CREDIT, Money(inr, 100))
        )
        val journal = proposal(original)
        original.clear()
        assertEquals(2, journal.lines.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (journal.lines as MutableList).clear()
        }
    }

    @Test fun reversalHasNewCommandIdReasonAndOppositeSides() {
        val posted = PostedJournalSnapshot("server-journal-123", Instant.parse("2026-11-07T00:00:00Z"), proposal())
        val reverse = ReversalPlanner.propose(posted, "command-002", "reversal-bill-001", 2, YearMonth.of(2026, 12), "owner", "Wrong vendor")
        JournalValidator.validate(reverse, chart)
        assertEquals("server-journal-123", reverse.reversalOfJournalId)
        assertEquals("Wrong vendor", reverse.correctionReason)
        assertEquals(YearMonth.of(2026, 12), reverse.accountingPeriod)
        assertEquals(YearMonth.of(2026, 11), posted.proposal.accountingPeriod)
        assertEquals(EntrySide.CREDIT, reverse.lines[0].side)
        assertEquals(EntrySide.DEBIT, reverse.lines[2].side)
        assertEquals(EntrySide.DEBIT, posted.proposal.lines[0].side)
    }

    @Test fun reversalWithoutReasonIsRejected() {
        val posted = PostedJournalSnapshot("server-journal-123", Instant.EPOCH, proposal())
        assertThrows(IllegalArgumentException::class.java) {
            ReversalPlanner.propose(posted, "command-002", "reversal-bill-001", 2, YearMonth.of(2026, 12), "owner", "  ")
        }
    }

    @Test fun sourceAndCommandIdAreMandatory() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalProposal("", "bill-1", 1, scope, YearMonth.of(2026, 11), inr, "owner", emptyList())
        }
    }

    @Test fun fingerprintIsStableAcrossLineOrdering() {
        val p1 = proposal()
        val p2 = proposal(p1.lines.reversed())
        assertEquals(ProposalFingerprint.sha256(p1), ProposalFingerprint.sha256(p2))
    }

    @Test fun fingerprintChangesIfAmountChanges() {
        val p1 = proposal()
        val p2 = proposal(p1.lines.map { if (it.lineId == "l1") it.copy(amount = Money(inr, 25_000_001)) else it })
        assertNotEquals(ProposalFingerprint.sha256(p1), ProposalFingerprint.sha256(p2))
    }

    @Test fun idempotencyReplayAndPayloadReuseCheck() {
        val p = proposal()
        assertEquals(CommandDecision.NEW_COMMAND, IdempotencyPolicy.check(p, null))
        val existing = AppliedCommand(p.commandId, p.scope, ProposalFingerprint.sha256(p), "server-journal-123")
        assertEquals(CommandDecision.REPLAY_PRIOR_RESULT, IdempotencyPolicy.check(p, existing))
        val different = proposal(p.lines.map { if (it.lineId == "l3") it.copy(amount = Money(inr, 30_000_000)) else it })
        assertThrows(IllegalArgumentException::class.java) { IdempotencyPolicy.check(different, existing) }
    }

    @Test fun balanceOverflowFailsInsteadOfSilentlyWrapping() {
        val wrong = proposal(listOf(
            JournalLine("d1", "rent", EntrySide.DEBIT, Money(inr, Long.MAX_VALUE)),
            JournalLine("d2", "gst", EntrySide.DEBIT, Money(inr, 1)),
            JournalLine("c1", "payable", EntrySide.CREDIT, Money(inr, Long.MAX_VALUE))
        ))
        assertThrows(ArithmeticException::class.java) { JournalValidator.validate(wrong, chart) }
    }

    @Test fun correctionReasonIsIncludedInIdempotencyFingerprint() {
        val original = PostedJournalSnapshot("journal-1", Instant.EPOCH, proposal())
        val a = ReversalPlanner.propose(original, "command-2", "reverse", 2, YearMonth.of(2026, 12), "owner", "Wrong category")
        val b = ReversalPlanner.propose(original, "command-2", "reverse", 2, YearMonth.of(2026, 12), "owner", "Wrong vendor")
        assertNotEquals(ProposalFingerprint.sha256(a), ProposalFingerprint.sha256(b))
    }

    @Test fun repeatedReversalCannotReuseOriginalCommandId() {
        val original = PostedJournalSnapshot("journal-1", Instant.EPOCH, proposal())
        assertThrows(IllegalArgumentException::class.java) {
            ReversalPlanner.propose(original, "command-001", "reverse", 2, YearMonth.of(2026, 12), "owner", "Wrong vendor")
        }
    }
    @Test fun decimalTextRoundTripsWithoutFloatingPoint() {
        val value = Money.parseMajorUnits(inr, "250000.25")
        assertEquals(25_000_025L, value.minorUnits)
        assertEquals("250000.25", value.toMajorUnitsText())
        assertEquals("-0.01", Money(inr, -1).toMajorUnitsText())
    }

    @Test fun malformedMoneyTextIsRejected() {
        for (bad in listOf("0.001", "1e5", "NaN", "12,345.00", "01.23", "-")) {
            assertThrows(IllegalArgumentException::class.java) { Money.parseMajorUnits(inr, bad) }
        }
    }

    @Test fun moneyTextRejectsLongOverflow() {
        assertThrows(ArithmeticException::class.java) {
            Money.parseMajorUnits(inr, "92233720368547759.00")
        }
    }

    @Test fun moneyTextSupportsLongMinimumValue() {
        val value = Money(inr, Long.MIN_VALUE)
        assertEquals(value, Money.parseMajorUnits(inr, value.toMajorUnitsText()))
    }

    @Test fun crossTenantIdempotencyRecordCannotBeReplayed() {
        val p = proposal()
        val applied = AppliedCommand(p.commandId, scope.copy(tenantId = "tenant-b"), ProposalFingerprint.sha256(p), "server-journal-1")
        assertThrows(IllegalArgumentException::class.java) { IdempotencyPolicy.check(p, applied) }
    }

}

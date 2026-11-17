package com.example.bookingregister.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class Legacy103UpgradeNoticePolicyTest {
    private fun verifiedEvidence() = Legacy103UpgradeNoticeEvidence(
        originalBookingPaymentAudit = Legacy103AuditReport(
            originalBookings = 2,
            originalPayments = 1,
            matchedBookings = 2,
            matchedPayments = 1,
            issues = emptyList()
        ),
        propertyReadinessAudit = Legacy103PropertyReadinessReport(
            inventoryReconciled = true,
            cloudRecordCounts = mapOf("rooms" to 2, "bookings" to 2),
            issues = emptyList()
        ),
        migrationGate = LegacyPropertyMigrationDecision(true, emptyList()),
        legacy103OriginVerified = true,
        serverOwnerPermissionVerified = true,
        freshServerSnapshotVerified = true,
        immutableOffDeviceBackupVerified = true,
        serverWriteExclusionVerified = true,
        migrationServiceReady = true,
        noPriorPropertyMigration = true,
        noticeNotPreviouslyHandled = true
    )

    private fun blocked(e: Legacy103UpgradeNoticeEvidence, reason: String) {
        val decision = Legacy103UpgradeNoticePolicy.evaluate(e)
        assertFalse(decision.mayOfferNotice)
        assertTrue("Expected $reason in ${decision.blockers}", reason in decision.blockers)
    }

    @Test fun fullyVerifiedLegacyOwnerMaySeeFutureNotice() {
        val decision = Legacy103UpgradeNoticePolicy.evaluate(verifiedEvidence())
        assertTrue(decision.mayOfferNotice)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test fun modernAccountMustNeverSeeNotice() = blocked(
        verifiedEvidence().copy(legacy103OriginVerified = false), "NOT_VERIFIED_LEGACY_103"
    )

    @Test fun staffCannotSeeOwnerNotice() = blocked(
        verifiedEvidence().copy(serverOwnerPermissionVerified = false), "NOT_VERIFIED_OWNER"
    )

    @Test fun originalBookingOrPaymentMismatchBlocks() = blocked(
        verifiedEvidence().copy(originalBookingPaymentAudit =
            verifiedEvidence().originalBookingPaymentAudit.copy(matchedBookings = 1)),
        "ORIGINAL_BOOKINGS_OR_PAYMENTS_NOT_RECONCILED"
    )

    @Test fun missingReadinessAuditBlocks() = blocked(
        verifiedEvidence().copy(propertyReadinessAudit =
            Legacy103PropertyReadinessReport.blocked("LOCAL_INVENTORY_READ_FAILED")),
        "PROPERTY_INVENTORY_NOT_RECONCILED"
    )

    @Test fun readinessIssuesCannotBeIgnored() = blocked(
        verifiedEvidence().copy(propertyReadinessAudit =
            Legacy103PropertyReadinessReport(true, emptyMap(), listOf("BROKEN_REFERENCE"))),
        "PROPERTY_INVENTORY_NOT_RECONCILED"
    )

    @Test fun migrationGateBlockersPreventNoticeEvenWhenFlagTrue() = blocked(
        verifiedEvidence().copy(migrationGate =
            LegacyPropertyMigrationDecision(true, listOf("UNSYNCED_RECORDS_PRESENT"))),
        "MIGRATION_PREFLIGHT_NOT_CLEARED"
    )

    @Test fun gateNotEligibleBlocks() = blocked(
        verifiedEvidence().copy(migrationGate = LegacyPropertyMigrationDecision(false, emptyList())),
        "MIGRATION_PREFLIGHT_NOT_CLEARED"
    )

    @Test fun staleServerAuditBlocks() = blocked(
        verifiedEvidence().copy(freshServerSnapshotVerified = false),
        "FRESH_SERVER_STATE_NOT_VERIFIED"
    )

    @Test fun missingImmutableBackupBlocks() = blocked(
        verifiedEvidence().copy(immutableOffDeviceBackupVerified = false),
        "IMMUTABLE_BACKUP_NOT_VERIFIED"
    )

    @Test fun concurrentWritesNotBlockedPreventsNotice() = blocked(
        verifiedEvidence().copy(serverWriteExclusionVerified = false),
        "CONCURRENT_WRITES_NOT_BLOCKED"
    )

    @Test fun noMigrationServiceMeansNoNotice() = blocked(
        verifiedEvidence().copy(migrationServiceReady = false),
        "MIGRATION_SERVICE_NOT_READY"
    )

    @Test fun alreadyMigratedAccountCannotSeeNotice() = blocked(
        verifiedEvidence().copy(noPriorPropertyMigration = false),
        "MIGRATION_ALREADY_PERFORMED"
    )

    @Test fun alreadyHandledNoticeCannotRepeat() = blocked(
        verifiedEvidence().copy(noticeNotPreviouslyHandled = false),
        "NOTICE_ALREADY_HANDLED"
    )

    @Test fun multipleFailuresAreAllReportedForInternalDiagnostics() {
        val e = verifiedEvidence().copy(
            legacy103OriginVerified = false,
            migrationServiceReady = false,
            serverWriteExclusionVerified = false
        )
        val decision = Legacy103UpgradeNoticePolicy.evaluate(e)
        assertFalse(decision.mayOfferNotice)
        assertTrue(decision.blockers.size == 3)
    }
}

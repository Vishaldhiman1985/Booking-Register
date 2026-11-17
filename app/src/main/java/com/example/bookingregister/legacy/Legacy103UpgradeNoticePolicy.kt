package com.example.bookingregister.legacy

/**
 * Stage 3: an isolated, fail-closed policy for the future owner upgrade notice.
 * No network/database access, UI side effects, or booking/sync mutations.
 *
 * Evidence must be supplied by authoritative, current server-backed checks.
 * This class does not fetch or manufacture that evidence. In Code 21 it has
 * no caller in login, booking screens, or synchronization.
 */
internal data class Legacy103UpgradeNoticeEvidence(
    val originalBookingPaymentAudit: Legacy103AuditReport,
    val propertyReadinessAudit: Legacy103PropertyReadinessReport,
    val migrationGate: LegacyPropertyMigrationDecision,
    val legacy103OriginVerified: Boolean,
    val serverOwnerPermissionVerified: Boolean,
    val freshServerSnapshotVerified: Boolean,
    val immutableOffDeviceBackupVerified: Boolean,
    val serverWriteExclusionVerified: Boolean,
    val migrationServiceReady: Boolean,
    val noPriorPropertyMigration: Boolean,
    val noticeNotPreviouslyHandled: Boolean
)

internal data class Legacy103UpgradeNoticeDecision(
    val mayOfferNotice: Boolean,
    val blockers: List<String>
)

internal object Legacy103UpgradeNoticePolicy {
    fun evaluate(e: Legacy103UpgradeNoticeEvidence): Legacy103UpgradeNoticeDecision {
        val blockers = mutableListOf<String>()
        if (!e.legacy103OriginVerified) blockers += "NOT_VERIFIED_LEGACY_103"
        if (!e.serverOwnerPermissionVerified) blockers += "NOT_VERIFIED_OWNER"
        if (!e.originalBookingPaymentAudit.snapshotReconciled) {
            blockers += "ORIGINAL_BOOKINGS_OR_PAYMENTS_NOT_RECONCILED"
        }
        if (!e.propertyReadinessAudit.inventoryReconciled ||
            e.propertyReadinessAudit.issues.isNotEmpty()
        ) blockers += "PROPERTY_INVENTORY_NOT_RECONCILED"
        if (!e.migrationGate.eligibleToOffer || e.migrationGate.blockers.isNotEmpty()) {
            blockers += "MIGRATION_PREFLIGHT_NOT_CLEARED"
        }
        if (!e.freshServerSnapshotVerified) blockers += "FRESH_SERVER_STATE_NOT_VERIFIED"
        if (!e.immutableOffDeviceBackupVerified) blockers += "IMMUTABLE_BACKUP_NOT_VERIFIED"
        if (!e.serverWriteExclusionVerified) blockers += "CONCURRENT_WRITES_NOT_BLOCKED"
        if (!e.migrationServiceReady) blockers += "MIGRATION_SERVICE_NOT_READY"
        if (!e.noPriorPropertyMigration) blockers += "MIGRATION_ALREADY_PERFORMED"
        if (!e.noticeNotPreviouslyHandled) blockers += "NOTICE_ALREADY_HANDLED"
        return Legacy103UpgradeNoticeDecision(
            mayOfferNotice = blockers.isEmpty(),
            blockers = blockers
        )
    }
}

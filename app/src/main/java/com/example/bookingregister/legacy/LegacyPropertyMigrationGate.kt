package com.example.bookingregister.legacy

/**
 * Safety policy only: neither performs queries nor changes local/cloud records.
 * Every 'verified' input must come from a fresh, authoritative audit, not a cache.
 */
internal data class LegacyPropertyMigrationPreflight(
    val legacyPlay103BackupPresent: Boolean,
    val ownerPermissionVerified: Boolean,
    val online: Boolean,
    val noRealManagedProperties: Boolean,
    val activeLegacyRooms: Int,
    val allActiveRoomsHaveNoPropertyId: Boolean,
    val immutableBackupVerified: Boolean,
    val allOriginalBookingIdsAndValuesMatchServer: Boolean,
    val allOriginalPaymentIdsAndValuesMatchServer: Boolean,
    val financialAndTaxLinesReconciled: Boolean,
    val allBookingRoomLinksValid: Boolean,
    val dependentRecordsAudited: Boolean,
    val noPendingOrFailedSync: Boolean,
    val noConcurrentBookingMutations: Boolean,
    val migrationNotAlreadyComplete: Boolean
)

internal data class LegacyPropertyMigrationDecision(
    val eligibleToOffer: Boolean,
    val blockers: List<String>
)

internal object LegacyPropertyMigrationGate {
    fun evaluate(p: LegacyPropertyMigrationPreflight): LegacyPropertyMigrationDecision {
        val blockers = mutableListOf<String>()
        if (!p.legacyPlay103BackupPresent) blockers += "NO_VERIFIED_PLAY_103_BACKUP"
        if (!p.ownerPermissionVerified) blockers += "OWNER_PERMISSION_NOT_VERIFIED"
        if (!p.online) blockers += "OFFLINE"
        if (!p.noRealManagedProperties) blockers += "PROPERTY_ALREADY_EXISTS"
        if (p.activeLegacyRooms < 1) blockers += "NO_ACTIVE_LEGACY_ROOMS"
        if (!p.allActiveRoomsHaveNoPropertyId) blockers += "ROOM_ALREADY_ASSIGNED_TO_PROPERTY"
        if (!p.immutableBackupVerified) blockers += "BACKUP_NOT_VERIFIED"
        if (!p.allOriginalBookingIdsAndValuesMatchServer) blockers += "BOOKINGS_NOT_FULLY_RECONCILED"
        if (!p.allOriginalPaymentIdsAndValuesMatchServer) blockers += "PAYMENTS_NOT_FULLY_RECONCILED"
        if (!p.financialAndTaxLinesReconciled) blockers += "FINANCIAL_RECORDS_NOT_VERIFIED"
        if (!p.allBookingRoomLinksValid) blockers += "BOOKING_ROOM_LINKS_NOT_VERIFIED"
        if (!p.dependentRecordsAudited) blockers += "DEPENDENT_RECORDS_NOT_AUDITED"
        if (!p.noPendingOrFailedSync) blockers += "UNSYNCED_RECORDS_PRESENT"
        if (!p.noConcurrentBookingMutations) blockers += "CONCURRENT_WRITES_NOT_BLOCKED"
        if (!p.migrationNotAlreadyComplete) blockers += "ALREADY_MIGRATED"
        return LegacyPropertyMigrationDecision(blockers.isEmpty(), blockers)
    }
}
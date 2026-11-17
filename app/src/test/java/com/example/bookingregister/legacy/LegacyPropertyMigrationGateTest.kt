package com.example.bookingregister.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LegacyPropertyMigrationGateTest {
    private fun ready() = LegacyPropertyMigrationPreflight(
        legacyPlay103BackupPresent = true,
        ownerPermissionVerified = true,
        online = true,
        noRealManagedProperties = true,
        activeLegacyRooms = 3,
        allActiveRoomsHaveNoPropertyId = true,
        immutableBackupVerified = true,
        allOriginalBookingIdsAndValuesMatchServer = true,
        allOriginalPaymentIdsAndValuesMatchServer = true,
        financialAndTaxLinesReconciled = true,
        allBookingRoomLinksValid = true,
        dependentRecordsAudited = true,
        noPendingOrFailedSync = true,
        noConcurrentBookingMutations = true,
        migrationNotAlreadyComplete = true
    )

    @Test fun fullyVerifiedLegacyHotelIsEligibleForNotice() {
        val result = LegacyPropertyMigrationGate.evaluate(ready())
        assertTrue(result.eligibleToOffer)
        assertTrue(result.blockers.isEmpty())
    }

    @Test fun existingMultiPropertyHotelIsBlocked() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(noRealManagedProperties = false))
        assertFalse(result.eligibleToOffer)
        assertTrue("PROPERTY_ALREADY_EXISTS" in result.blockers)
    }

    @Test fun unverifiedPaymentsBlockTheNotice() {
        val result = LegacyPropertyMigrationGate.evaluate(
            ready().copy(allOriginalPaymentIdsAndValuesMatchServer = false)
        )
        assertFalse(result.eligibleToOffer)
        assertTrue("PAYMENTS_NOT_FULLY_RECONCILED" in result.blockers)
    }

    @Test fun pendingOrFailedBookingSyncBlocksTheNotice() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(noPendingOrFailedSync = false))
        assertFalse(result.eligibleToOffer)
        assertTrue("UNSYNCED_RECORDS_PRESENT" in result.blockers)
    }

    @Test fun noLegacyBackupBlocksCode18Users() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(legacyPlay103BackupPresent = false))
        assertFalse(result.eligibleToOffer)
        assertTrue("NO_VERIFIED_PLAY_103_BACKUP" in result.blockers)
    }

    @Test fun cannotOfferWhileRecordWritesAreInFlight() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(noConcurrentBookingMutations = false))
        assertFalse(result.eligibleToOffer)
        assertTrue("CONCURRENT_WRITES_NOT_BLOCKED" in result.blockers)
    }

    @Test fun previousMigrationBlocksRepeat() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(migrationNotAlreadyComplete = false))
        assertFalse(result.eligibleToOffer)
        assertEquals(listOf("ALREADY_MIGRATED"), result.blockers)
    }

    @Test fun multipleProblemsAreReportedTogether() {
        val result = LegacyPropertyMigrationGate.evaluate(ready().copy(
            immutableBackupVerified = false,
            financialAndTaxLinesReconciled = false
        ))
        assertFalse(result.eligibleToOffer)
        assertEquals(2, result.blockers.size)
    }
}
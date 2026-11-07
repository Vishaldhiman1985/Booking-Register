package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDevSafetyGateTest {
    @Test
    fun acceptsOnlyExpectedOfflineStagingIdentity() {
        assertTrue(
            FinanceDevSafetyGate.violations(
                packageName = FinanceDevSafetyGate.EXPECTED_PACKAGE_NAME,
                firebaseProjectId = FinanceDevSafetyGate.EXPECTED_FIREBASE_PROJECT_ID,
                firebaseAppId = FinanceDevSafetyGate.EXPECTED_FIREBASE_APP_ID,
                internetPermissionGranted = false
            ).isEmpty()
        )
    }

    @Test
    fun rejectsProductionFirebaseFallback() {
        val violations = FinanceDevSafetyGate.violations(
            packageName = FinanceDevSafetyGate.EXPECTED_PACKAGE_NAME,
            firebaseProjectId = "booking-register-aadd3",
            firebaseAppId = "production-app-id",
            internetPermissionGranted = false
        )

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("Unexpected Firebase project") })
        assertTrue(violations.any { it.contains("Unexpected Firebase app") })
    }

    @Test
    fun rejectsInternetPermissionUntilBackendIsolationExists() {
        val violations = FinanceDevSafetyGate.violations(
            packageName = FinanceDevSafetyGate.EXPECTED_PACKAGE_NAME,
            firebaseProjectId = FinanceDevSafetyGate.EXPECTED_FIREBASE_PROJECT_ID,
            firebaseAppId = FinanceDevSafetyGate.EXPECTED_FIREBASE_APP_ID,
            internetPermissionGranted = true
        )

        assertEquals(
            listOf("Finance DEV cloud access must remain locked during Phase 0"),
            violations
        )
    }
}

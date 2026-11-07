package com.example.bookingregister.finance.dev

object FinanceDevSafetyGate {
    const val EXPECTED_PACKAGE_NAME = "com.bookingregister.app.finance.dev"
    const val EXPECTED_FIREBASE_PROJECT_ID = "booking-register-staging"
    const val EXPECTED_FIREBASE_APP_ID = "1:1054392382300:android:34b9b39797eb7cf2680799"

    fun violations(
        packageName: String,
        firebaseProjectId: String?,
        firebaseAppId: String?,
        internetPermissionGranted: Boolean
    ): List<String> = buildList {
        if (packageName != EXPECTED_PACKAGE_NAME) {
            add("Unexpected application ID: $packageName")
        }
        if (firebaseProjectId != EXPECTED_FIREBASE_PROJECT_ID) {
            add("Unexpected Firebase project: ${firebaseProjectId ?: "missing"}")
        }
        if (firebaseAppId != EXPECTED_FIREBASE_APP_ID) {
            add("Unexpected Firebase app: ${firebaseAppId ?: "missing"}")
        }
        if (internetPermissionGranted) {
            add("Finance DEV cloud access must remain locked during Phase 0")
        }
    }

    fun requireSafe(
        packageName: String,
        firebaseProjectId: String?,
        firebaseAppId: String?,
        internetPermissionGranted: Boolean
    ) {
        val errors = violations(
            packageName = packageName,
            firebaseProjectId = firebaseProjectId,
            firebaseAppId = firebaseAppId,
            internetPermissionGranted = internetPermissionGranted
        )
        check(errors.isEmpty()) {
            "Finance DEV isolation check failed: ${errors.joinToString("; ")}"
        }
    }
}

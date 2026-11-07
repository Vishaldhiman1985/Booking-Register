package com.example.bookingregister.finance.dev

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp

class FinanceDevApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val firebaseApp = FirebaseApp.getInstance()
        FinanceDevSafetyGate.requireSafe(
            packageName = packageName,
            firebaseProjectId = firebaseApp.options.projectId,
            firebaseAppId = firebaseApp.options.applicationId,
            internetPermissionGranted = checkSelfPermission(Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
}

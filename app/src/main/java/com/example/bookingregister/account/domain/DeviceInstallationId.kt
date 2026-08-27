package com.example.bookingregister.account.domain

import android.content.Context
import java.util.UUID

object DeviceInstallationId {

    private const val PREFS_NAME = "booking_register_device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (existing != null) {
            return existing
        }

        val newId = UUID.randomUUID().toString()

        prefs.edit()
            .putString(KEY_INSTALLATION_ID, newId)
            .apply()

        return newId
    }
}
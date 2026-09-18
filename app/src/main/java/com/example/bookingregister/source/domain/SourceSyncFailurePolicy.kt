package com.example.bookingregister.source.domain

import com.example.bookingregister.data.sync.SyncFailureCode

enum class SourceSyncFailureDisposition {
    RETRYABLE,
    TERMINAL
}

/**
 * Source master-data sync policy.
 *
 * PENDING means there is still executable local intent.
 * FAILED is reserved for terminal source writes that must not loop forever.
 */
object SourceSyncFailurePolicy {

    fun disposition(code: SyncFailureCode): SourceSyncFailureDisposition =
        when (code) {
            SyncFailureCode.UNAUTHENTICATED,
            SyncFailureCode.UNAVAILABLE,
            SyncFailureCode.INTERNAL,
            SyncFailureCode.UNKNOWN -> SourceSyncFailureDisposition.RETRYABLE

            SyncFailureCode.PERMISSION_DENIED,
            SyncFailureCode.STALE_REVISION,
            SyncFailureCode.ALREADY_EXISTS,
            SyncFailureCode.INVALID_ARGUMENT,
            SyncFailureCode.FAILED_PRECONDITION,
            SyncFailureCode.NOT_FOUND,
            SyncFailureCode.ORPHANED_BOOKING_INTENT -> SourceSyncFailureDisposition.TERMINAL
        }

    fun shouldDiscardNeverSyncedLocalSource(
        code: SyncFailureCode,
        revision: Long,
        lastSyncedAt: Long?
    ): Boolean =
        code == SyncFailureCode.PERMISSION_DENIED &&
            revision <= 0L &&
            lastSyncedAt == null

    /**
     * Compatibility reader for failure text written by older app versions.
     * New writes use structured failure codes directly.
     */
    fun storedFailureCode(message: String?): SyncFailureCode? {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return null

        SyncFailureCode.entries.firstOrNull { code ->
            text.startsWith("[${code.name}]")
        }?.let { return it }

        return SyncFailureCode.entries.firstOrNull { code ->
            text == code.name ||
                text.startsWith("${code.name}:") ||
                text.startsWith("${code.name} ")
        }
    }
}

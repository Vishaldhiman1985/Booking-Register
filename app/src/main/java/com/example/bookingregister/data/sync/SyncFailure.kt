package com.example.bookingregister.data.sync

enum class SyncFailureCode {
    UNAUTHENTICATED, PERMISSION_DENIED, STALE_REVISION, ALREADY_EXISTS,
    INVALID_ARGUMENT, FAILED_PRECONDITION, NOT_FOUND, ORPHANED_BOOKING_INTENT,
    UNAVAILABLE, INTERNAL, UNKNOWN
}

interface CodedSyncFailure {
    val syncFailureCode: SyncFailureCode
}

class StructuredSyncException(
    override val syncFailureCode: SyncFailureCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause), CodedSyncFailure

fun syncFailureText(error: Throwable): String {
    val code = (error as? CodedSyncFailure)?.syncFailureCode ?: SyncFailureCode.UNKNOWN
    val message = error.message?.trim().orEmpty().ifBlank { "Sync failed. Please retry." }
    return "[${code.name}] $message"
}

fun isStaleRevisionFailure(message: String?): Boolean =
    message?.trim()?.startsWith("[${SyncFailureCode.STALE_REVISION.name}]") == true

fun isOrphanedBookingIntentFailure(message: String?): Boolean =
    message?.trim()?.startsWith("[${SyncFailureCode.ORPHANED_BOOKING_INTENT.name}]") == true

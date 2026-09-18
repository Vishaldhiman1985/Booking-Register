package com.example.bookingregister.data.sync

import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Provider-neutral Firestore error mapping.
 *
 * The String-based mapper is deliberately pure so it can be tested on the JVM
 * without initializing Firebase Android classes. The enum adapter is only the
 * Android/Firebase boundary.
 */
fun firestoreSyncFailureCodeName(codeName: String): SyncFailureCode =
    when (codeName.trim().uppercase()) {
        "UNAUTHENTICATED" ->
            SyncFailureCode.UNAUTHENTICATED

        "PERMISSION_DENIED" ->
            SyncFailureCode.PERMISSION_DENIED

        "ALREADY_EXISTS" ->
            SyncFailureCode.ALREADY_EXISTS

        "INVALID_ARGUMENT" ->
            SyncFailureCode.INVALID_ARGUMENT

        "FAILED_PRECONDITION" ->
            SyncFailureCode.FAILED_PRECONDITION

        "NOT_FOUND" ->
            SyncFailureCode.NOT_FOUND

        "CANCELLED",
        "DEADLINE_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "ABORTED",
        "UNAVAILABLE" ->
            SyncFailureCode.UNAVAILABLE

        "INTERNAL",
        "DATA_LOSS" ->
            SyncFailureCode.INTERNAL

        "OK",
        "UNKNOWN",
        "OUT_OF_RANGE",
        "UNIMPLEMENTED" ->
            SyncFailureCode.UNKNOWN

        else ->
            SyncFailureCode.UNKNOWN
    }

fun firestoreSyncFailureCode(
    code: FirebaseFirestoreException.Code
): SyncFailureCode =
    firestoreSyncFailureCodeName(code.name)

fun Throwable.toStructuredFirestoreSyncException(): Throwable {
    if (this is CodedSyncFailure) return this

    val firestoreError = this as? FirebaseFirestoreException
        ?: return StructuredSyncException(
            SyncFailureCode.UNKNOWN,
            message?.trim().orEmpty().ifBlank { "Firestore sync failed." },
            this
        )

    val code = firestoreSyncFailureCodeName(firestoreError.code.name)
    val meaningfulMessage = firestoreError.message
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: when (code) {
            SyncFailureCode.PERMISSION_DENIED ->
                "You do not have permission to change this data."
            SyncFailureCode.UNAUTHENTICATED ->
                "Authentication is required before this data can be synced."
            SyncFailureCode.UNAVAILABLE ->
                "The sync service is temporarily unavailable. Local data is preserved."
            SyncFailureCode.INTERNAL ->
                "The sync service could not complete this operation."
            else ->
                "Firestore sync failed (${code.name})."
        }

    return StructuredSyncException(code, meaningfulMessage, this)
}

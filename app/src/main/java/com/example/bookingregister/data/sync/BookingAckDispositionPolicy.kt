package com.example.bookingregister.data.sync

import com.example.bookingregister.booking.domain.BookingChangeSet

/**
 * Classifies a booking after its upload has been acknowledged.
 *
 * Never treats a newer local version as successfully uploaded.
 * A changed booking without another durable upload instruction
 * requires safe reconciliation instead of an ordinary pending state.
 */
object BookingAckDispositionPolicy {

    enum class OperationDisposition {
        APPLY,
        IGNORE_ALREADY_ACKNOWLEDGED
    }

    enum class Disposition {
        SYNCED,
        PENDING_WITH_OUTBOX,
        RECONCILIATION_REQUIRED
    }

    fun operationDisposition(operationWasRemoved: Boolean): OperationDisposition =
        if (operationWasRemoved) {
            OperationDisposition.APPLY
        } else {
            OperationDisposition.IGNORE_ALREADY_ACKNOWLEDGED
        }

    fun decide(
        sentVersionIsCurrent: Boolean,
        hasLaterOperation: Boolean
    ): Disposition = when {
        hasLaterOperation -> Disposition.PENDING_WITH_OUTBOX
        sentVersionIsCurrent -> Disposition.SYNCED
        else -> Disposition.RECONCILIATION_REQUIRED
    }

    /**
     * Child rows are independently versioned. An unchanged row is covered by this
     * acknowledgement even when a later booking-only command exists. A changed row
     * may remain pending only when that later durable command can carry it.
     */
    fun decideChildRow(
        sentVersionIsCurrent: Boolean,
        hasLaterCoveringOperation: Boolean
    ): Disposition = when {
        sentVersionIsCurrent -> Disposition.SYNCED
        hasLaterCoveringOperation -> Disposition.PENDING_WITH_OUTBOX
        else -> Disposition.RECONCILIATION_REQUIRED
    }

    fun operationCoversFinancialLines(changeSetJson: String): Boolean =
        changeSetJson.isBlank() ||
            runCatching {
                val changeSet = BookingChangeSet.fromJson(changeSetJson)
                changeSet.create || changeSet.rebuildFinancialLines
            }.getOrElse {
                // An unreadable durable command must be treated conservatively as covering
                // the child row so reconciliation cannot overwrite unknown local intent.
                true
            }

    fun operationCoversRoomPlan(changeSetJson: String): Boolean =
        changeSetJson.isBlank() ||
            runCatching {
                BookingChangeSet.fromJson(changeSetJson).roomPlanAssignments != null
            }.getOrElse { true }
}

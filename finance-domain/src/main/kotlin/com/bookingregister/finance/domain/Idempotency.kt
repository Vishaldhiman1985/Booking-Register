package com.bookingregister.finance.domain

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Canonical versioned fingerprint; no floating point or locale-dependent serialization. */
object ProposalFingerprint {
    fun sha256(proposal: JournalProposal): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { writer ->
            writer.writeInt(1) // fingerprint format version
            writer.writeString(proposal.commandId)
            writer.writeString(proposal.sourceId)
            writer.writeLong(proposal.sourceRevision)
            writer.writeString(proposal.scope.environment.name)
            writer.writeString(proposal.scope.tenantId)
            writer.writeString(proposal.scope.hotelId)
            writer.writeString(proposal.scope.propertyId)
            writer.writeString(proposal.accountingPeriod.toString())
            writer.writeString(proposal.currency.name)
            writer.writeString(proposal.actorId)
            writer.writeString(proposal.reversalOfJournalId)
            writer.writeString(proposal.correctionReason)
            val orderedLines = proposal.lines.sortedBy { it.lineId }
            writer.writeInt(orderedLines.size)
            for (line in orderedLines) {
                writer.writeString(line.lineId)
                writer.writeString(line.accountId)
                writer.writeString(line.side.name)
                writer.writeString(line.amount.currency.name)
                writer.writeLong(line.amount.minorUnits)
                writer.writeString(line.memo)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(output.toByteArray())
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 255
                append(hex[value ushr 4])
                append(hex[value and 15])
            }
        }
    }

    private fun DataOutputStream.writeString(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }
}

/** Records for checking replay policy; actual records must be persisted in a server transaction. */
data class AppliedCommand(
    val commandId: String,
    val scope: FinanceScope,
    val payloadFingerprint: String,
    val serverJournalId: String
)

enum class CommandDecision { NEW_COMMAND, REPLAY_PRIOR_RESULT }

object IdempotencyPolicy {
    fun check(proposal: JournalProposal, applied: AppliedCommand?): CommandDecision {
        if (applied == null) return CommandDecision.NEW_COMMAND
        require(applied.commandId == proposal.commandId && applied.scope == proposal.scope) {
            "Applied command identity/scope mismatch"
        }
        require(applied.payloadFingerprint == ProposalFingerprint.sha256(proposal)) {
            "Command ID reused with a different payload"
        }
        return CommandDecision.REPLAY_PRIOR_RESULT
    }
}

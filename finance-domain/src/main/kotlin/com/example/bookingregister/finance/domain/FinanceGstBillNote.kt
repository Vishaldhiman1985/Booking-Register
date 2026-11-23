package com.example.bookingregister.finance.domain

/**
 * V19 compatibility metadata for the existing Finance DEV draft notes column.
 * This is an invoice-presence declaration, NOT a GST calculation, ITC decision or tax posting.
 * Markers are human-readable until the future audited invoice schema is introduced.
 * All previous notes remain unchanged when the user has not made a declaration.
 */
object FinanceGstBillNote {
    enum class Status { NOT_RECORDED, YES, NO }

    data class Parsed(val status: Status, val notes: String)

    private const val YES_MARKER = "[GST Bill: Yes]"
    private const val NO_MARKER = "[GST Bill: No]"

    fun read(raw: String): Parsed {
        val value = raw.trim()
        val status = when {
            value == YES_MARKER || value.endsWith("\n$YES_MARKER") -> Status.YES
            value == NO_MARKER || value.endsWith("\n$NO_MARKER") -> Status.NO
            else -> Status.NOT_RECORDED
        }
        val marker = when (status) {
            Status.YES -> YES_MARKER
            Status.NO -> NO_MARKER
            Status.NOT_RECORDED -> null
        }
        val clean = if (marker == null) raw else value.removeSuffix(marker).trimEnd('\n', '\r')
        return Parsed(status, clean)
    }

    fun write(status: Status, noteText: String): String {
        // Remove only a marker that this feature previously wrote; retain ordinary free text.
        val notes = read(noteText).notes.trim()
        val marker = when (status) {
            Status.NOT_RECORDED -> null
            Status.YES -> YES_MARKER
            Status.NO -> NO_MARKER
        }
        val result = when {
            marker == null -> notes
            notes.isBlank() -> marker
            else -> "$notes\n$marker"
        }
        require(result.length <= 2000) { "Notes and GST bill declaration exceed 2000 characters" }
        return result
    }
}

package com.example.bookingregister.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.YearMonth

/** Pure, read-only periodic weighted-average stock-cost projection; NOT a general ledger posting. */
object FinanceV22StockCostPolicy {
    data class Quantity(
        val itemId: String, val name: String, val unit: String,
        val opening: Double, val received: Double, val closing: Double
    )
    data class Evidence(
        val openingUnitPaise: Long?, val receivedUnitPaise: Long?,
        val openingReference: String, val receivedReference: String
    )
    data class Valued(
        val itemId: String, val openingValuePaise: Long, val receivedValuePaise: Long,
        val closingValuePaise: Long, val consumedValuePaise: Long, val consumedQuantity: Double
    )
    data class Totals(val openingPaise: Long, val receivedPaise: Long,
                      val closingPaise: Long, val consumedPaise: Long, val items: Int)
    private fun quantity(value: Double): BigDecimal {
        require(value.isFinite() && value >= 0.0) { "Invalid stock quantity" }
        return BigDecimal.valueOf(value)
    }
    private fun round(value: BigDecimal): Long =
        value.setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun requireEvidence(line: Quantity, evidence: Evidence) {
        require(line.itemId.isNotBlank() && line.name.isNotBlank() && line.unit.isNotBlank()) { "Missing item identity or unit" }
        val start = quantity(line.opening)
        val received = quantity(line.received)
        val ending = quantity(line.closing)
        require(ending <= start.add(received)) { "${line.name}: closing exceeds available quantity" }
        if (start.signum() > 0) {
            require(evidence.openingUnitPaise != null && evidence.openingUnitPaise > 0L) { "${line.name}: opening unit cost required" }
            require(evidence.openingReference.trim().length in 5..160) { "${line.name}: opening cost supporting reference required" }
        }
        if (received.signum() > 0) {
            require(evidence.receivedUnitPaise != null && evidence.receivedUnitPaise > 0L) { "${line.name}: receipt unit cost required" }
            require(evidence.receivedReference.trim().length in 5..160) { "${line.name}: purchase invoice/reference required" }
        }
        evidence.openingUnitPaise?.let { require(it > 0 && it <= 1_000_000_000_000L) { "Invalid opening cost" } }
        evidence.receivedUnitPaise?.let { require(it > 0 && it <= 1_000_000_000_000L) { "Invalid receipt cost" } }
    }

    /** Paise conservation: period opening + receipts = closing + consumption, including rounding. */
    fun value(line: Quantity, evidence: Evidence): Valued {
        requireEvidence(line, evidence)
        val start = quantity(line.opening)
        val received = quantity(line.received)
        val ending = quantity(line.closing)
        val available = start.add(received)
        val openingExact = start.multiply(BigDecimal.valueOf(evidence.openingUnitPaise ?: 0))
        val receivedExact = received.multiply(BigDecimal.valueOf(evidence.receivedUnitPaise ?: 0))
        val exactTotal = openingExact.add(receivedExact)
        val openingPaise = round(openingExact)
        val receivedPaise = round(receivedExact)
        val periodTotal = Math.addExact(openingPaise, receivedPaise)
        val closing = if (available.signum() == 0) 0L else round(
            exactTotal.multiply(ending).divide(available, 12, RoundingMode.HALF_UP)
        ).coerceIn(0L, periodTotal)
        return Valued(line.itemId, openingPaise, receivedPaise, closing,
            Math.subtractExact(periodTotal, closing), available.subtract(ending).toDouble())
    }

    fun totals(values: List<Valued>): Totals {
        require(values.map { it.itemId }.distinct().size == values.size) { "Duplicate item values" }
        fun sum(f: (Valued) -> Long) = values.fold(0L) { total, line -> Math.addExact(total, f(line)) }
        val opening = sum { it.openingValuePaise }
        val received = sum { it.receivedValuePaise }
        val closing = sum { it.closingValuePaise }
        val consumed = sum { it.consumedValuePaise }
        require(Math.addExact(opening, received) == Math.addExact(closing, consumed)) { "Stock valuation does not balance" }
        return Totals(opening, received, closing, consumed, values.size)
    }

    /** Fingerprint depends on every stored quantity, receipt ID and approval time, not just receipt IDs. */
    fun fingerprint(month: String, approvedAtMillis: Long, lines: List<Quantity>, receiptIds: List<String>): String {
        YearMonth.parse(month)
        require(approvedAtMillis > 0) { "Stock month is not approved" }
        require(lines.map { it.itemId }.distinct().size == lines.size) { "Duplicate stock items" }
        val text = buildString {
            append("V22|").append(month).append('|').append(approvedAtMillis).append('\n')
            lines.sortedBy { it.itemId }.forEach { item ->
                require(item.itemId.isNotBlank() && item.unit.isNotBlank())
                append(item.itemId).append('|').append(item.name).append('|').append(item.unit)
                    .append('|').append(quantity(item.opening).toPlainString())
                    .append('|').append(quantity(item.received).toPlainString())
                    .append('|').append(quantity(item.closing).toPlainString()).append('\n')
            }
            receiptIds.sorted().forEach { append("RECEIPT|").append(it).append('\n') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun rupeesToPaise(raw: String): Long {
        val value = raw.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid unit price in rupees")
        require(value > BigDecimal.ZERO && value <= BigDecimal("10000000000")) { "Unit price must be positive and within limits" }
        return value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }
}

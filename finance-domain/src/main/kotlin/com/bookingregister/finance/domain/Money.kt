package com.bookingregister.finance.domain

import java.math.BigInteger

/** All amounts are integer minor units. No floating-point values are accepted by this module. */
enum class CurrencyCode { INR }

data class Money(val currency: CurrencyCode, val minorUnits: Long) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies" }
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies" }
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    fun negate(): Money = copy(minorUnits = Math.negateExact(minorUnits))

    /** Exact UI text: 250000.25 means INR 250000 and 25 paise, never a Double. */
    fun toMajorUnitsText(): String {
        val absolute = BigInteger.valueOf(minorUnits).abs()
        val major = absolute.divide(BigInteger.valueOf(100L))
        val fractional = absolute.remainder(BigInteger.valueOf(100L)).toString().padStart(2, '0')
        return "${if (minorUnits < 0L) "-" else ""}$major.$fractional"
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(currency, 0)

        /** Only accepts plain decimal text with up to two fractional digits. */
        fun parseMajorUnits(currency: CurrencyCode, text: String): Money {
            val clean = text.trim()
            require(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,2})?").matches(clean)) {
                "Money must have at most two decimal digits"
            }
            val negative = clean.startsWith('-')
            val unsigned = if (negative) clean.substring(1) else clean
            val parts = unsigned.split('.')
            val whole = BigInteger(parts[0]).multiply(BigInteger.valueOf(100L))
            val fraction = BigInteger(parts.getOrElse(1) { "0" }.padEnd(2, '0'))
            val magnitude = whole.add(fraction)
            return Money(currency, (if (negative) magnitude.negate() else magnitude).longValueExact())
        }
    }
}

enum class MinorUnitRounding { HALF_UP, HALF_EVEN }

/** 10000 basis points = 100%. Works with paise and uses BigInteger for the intermediate product. */
class BasisPoints(val value: Int) {
    init {
        require(value in 0..10_000) { "Rate must be between 0 and 10000 basis points" }
    }

    fun of(amount: Money, rounding: MinorUnitRounding = MinorUnitRounding.HALF_UP): Money {
        val denominator = BigInteger.valueOf(10_000L)
        val numerator = BigInteger.valueOf(amount.minorUnits)
            .multiply(BigInteger.valueOf(value.toLong()))
        val (whole, remainder) = numerator.divideAndRemainder(denominator)
        val comparison = remainder.abs().multiply(BigInteger.valueOf(2L)).compareTo(denominator)
        val increment = comparison > 0 || (comparison == 0 &&
            (rounding == MinorUnitRounding.HALF_UP || whole.abs().testBit(0)))
        val rounded = if (increment) whole.add(BigInteger.valueOf(numerator.signum().toLong())) else whole
        return Money(amount.currency, rounded.longValueExact())
    }
}

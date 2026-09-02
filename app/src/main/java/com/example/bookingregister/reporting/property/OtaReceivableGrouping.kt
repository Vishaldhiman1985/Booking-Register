package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.entities.BookingSourceType
import java.util.Locale

data class OtaReceivableGroup(
    val sourceName: String,
    val sourceRemoteId: String?,
    val bookingCount: Int,
    val totalOutstanding: Double,
    val bookings: List<PropertyBalanceBookingFacts>
)

/**
 * Pure read-only projection of already-calculated Balance facts.
 *
 * It groups pending OTA receivables for display only.
 * It never determines receivable amounts and never changes payments.
 */
object OtaReceivableGrouping {

    fun build(
        rows: List<PropertyBalanceBookingFacts>
    ): List<OtaReceivableGroup> {

        return rows
            .asSequence()
            .filter {
                it.sourceType == BookingSourceType.OTA &&
                    it.outstanding > 0.001
            }
            .groupBy { row ->
                row.sourceName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.lowercase(Locale.ROOT)
                    ?: "ota"
            }
            .map { (_, groupedRows) ->

                val ordered = groupedRows.sortedWith(
                    compareBy<PropertyBalanceBookingFacts>(
                        { it.guestName.lowercase(Locale.ROOT) },
                        { it.bookingRemoteId }
                    )
                )

                val sourceIds = ordered
                    .mapNotNull {
                        it.sourceRemoteId
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                    }
                    .distinct()

                OtaReceivableGroup(
                    sourceName =
                        ordered.firstNotNullOfOrNull {
                            it.sourceName
                                ?.trim()
                                ?.takeIf(String::isNotEmpty)
                        } ?: "OTA",
                    sourceRemoteId = sourceIds.singleOrNull(),
                    bookingCount = ordered.size,
                    totalOutstanding =
                        ordered.sumOf { it.outstanding },
                    bookings = ordered
                )
            }
            .sortedBy {
                it.sourceName.lowercase(Locale.ROOT)
            }
    }
}

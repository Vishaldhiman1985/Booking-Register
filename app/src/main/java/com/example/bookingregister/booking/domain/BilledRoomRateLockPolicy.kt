package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import kotlin.math.abs

/** Protects the financial facts represented by an issued room bill. */
object BilledRoomRateLockPolicy {
    private const val MONEY_TOLERANCE = 0.01

    fun bookingFinancialsChanged(
        persisted: BookingEntity,
        candidate: BookingEntity
    ): Boolean {
        return moneyChanged(persisted.rate, candidate.rate) ||
                moneyChanged(persisted.receivable, candidate.receivable) ||
                moneyChanged(persisted.grossCharges, candidate.grossCharges) ||
                moneyChanged(persisted.roomRevenue, candidate.roomRevenue) ||
                moneyChanged(persisted.propertyTax, candidate.propertyTax) ||
                moneyChanged(persisted.commissionAmount, candidate.commissionAmount) ||
                moneyChanged(persisted.commissionTax, candidate.commissionTax) ||
                moneyChanged(persisted.sourceFee, candidate.sourceFee) ||
                moneyChanged(persisted.tdsAmount, candidate.tdsAmount) ||
                moneyChanged(persisted.tcsAmount, candidate.tcsAmount) ||
                moneyChanged(persisted.expectedPayout, candidate.expectedPayout)
    }

    fun financialLinesChanged(
        persisted: List<BookingFinancialLineEntity>,
        candidate: List<BookingFinancialLineEntity>
    ): Boolean {
        val persistedFacts = persisted.filter { !it.isDeleted }.map { it.toLockedFact() }.sortedBy { it.remoteId }
        val candidateFacts = candidate.filter { !it.isDeleted }.map { it.toLockedFact() }.sortedBy { it.remoteId }
        if (persistedFacts.size != candidateFacts.size) return true

        return persistedFacts.zip(candidateFacts).any { (old, new) ->
            old.remoteId != new.remoteId ||
                    old.roomRemoteId != new.roomRemoteId ||
                    old.propertyRemoteId != new.propertyRemoteId ||
                    old.businessDateMillis != new.businessDateMillis ||
                    moneyChanged(old.grossAmount, new.grossAmount) ||
                    moneyChanged(old.taxableAmount, new.taxableAmount) ||
                    moneyChanged(old.gstRatePercent, new.gstRatePercent) ||
                    moneyChanged(old.gstAmount, new.gstAmount) ||
                    old.hsnSacCode != new.hsnSacCode ||
                    old.slabRemoteId != new.slabRemoteId ||
                    old.slabName != new.slabName ||
                    moneyChanged(old.cgstRatePercent, new.cgstRatePercent) ||
                    moneyChanged(old.sgstRatePercent, new.sgstRatePercent) ||
                    moneyChanged(old.cessRatePercent, new.cessRatePercent) ||
                    moneyChanged(old.cgstAmount, new.cgstAmount) ||
                    moneyChanged(old.sgstAmount, new.sgstAmount) ||
                    moneyChanged(old.cessAmount, new.cessAmount) ||
                    old.source != new.source
        }
    }

    private fun moneyChanged(old: Double, new: Double): Boolean {
        return abs(old - new) > MONEY_TOLERANCE
    }

    private fun BookingFinancialLineEntity.toLockedFact() = LockedFinancialLineFact(
        remoteId = remoteId,
        roomRemoteId = roomRemoteId,
        propertyRemoteId = propertyRemoteId,
        businessDateMillis = businessDateMillis,
        grossAmount = grossAmount,
        taxableAmount = taxableAmount,
        gstRatePercent = gstRatePercent,
        gstAmount = gstAmount,
        hsnSacCode = hsnSacCode,
        slabRemoteId = slabRemoteId,
        slabName = slabName,
        cgstRatePercent = cgstRatePercent,
        sgstRatePercent = sgstRatePercent,
        cessRatePercent = cessRatePercent,
        cgstAmount = cgstAmount,
        sgstAmount = sgstAmount,
        cessAmount = cessAmount,
        source = source
    )

    private data class LockedFinancialLineFact(
        val remoteId: String,
        val roomRemoteId: String,
        val propertyRemoteId: String?,
        val businessDateMillis: Long,
        val grossAmount: Double,
        val taxableAmount: Double,
        val gstRatePercent: Double,
        val gstAmount: Double,
        val hsnSacCode: String?,
        val slabRemoteId: String?,
        val slabName: String?,
        val cgstRatePercent: Double,
        val sgstRatePercent: Double,
        val cessRatePercent: Double,
        val cgstAmount: Double,
        val sgstAmount: Double,
        val cessAmount: Double,
        val source: String
    )
}

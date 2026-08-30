package com.example.bookingregister.data.sync

data class OtaSettlementRequestSelection(
    val bookingRemoteId: String,
    val expectedOutstanding: Double
)

data class OtaSettlementWriteAllocation(
    val bookingRemoteId: String,
    val paymentRemoteId: String,
    val amount: Double,
    val paymentRevision: Long,
    val paymentMillis: Long,
    val paymentUpdatedAt: Long,
    val paymentMethod: String?,
    val paymentNote: String?
)

data class OtaSettlementWriteResult(
    val settlementRemoteId: String,
    val propertyRemoteId: String,
    val sourceRemoteId: String,
    val sourceName: String,
    val totalAmount: Double,
    val bookingCount: Int,
    val settlementMillis: Long,
    val updatedAt: Long,
    val updatedByUid: String?,
    val alreadyApplied: Boolean,
    val allocations: List<OtaSettlementWriteAllocation>
)
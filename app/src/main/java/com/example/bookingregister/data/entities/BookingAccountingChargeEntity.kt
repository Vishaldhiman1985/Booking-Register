package com.example.bookingregister.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_accounting_charges",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["hotelRemoteId"]),
        Index(value = ["bookingRemoteId"]),
        Index(value = ["chargeType"]),
        Index(value = ["hotelRemoteId", "bookingRemoteId"]),
        Index(value = ["hotelRemoteId", "chargeMillis"])
    ]
)
data class BookingAccountingChargeEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val remoteId: String,
    val hotelRemoteId: String,
    val bookingRemoteId: String,
    val chargeType: String,
    val accountBucket: String? = null,
    val amount: Double,
    val description: String,
    val reason: String? = null,
    val hsnSacCode: String? = null,
    val gstRatePercent: Double = 0.0,
    val taxInclusive: Boolean = true,
    val taxableAmount: Double? = null,
    val linkedFinalBillId: String? = null,
    val archivedAt: Long? = null,
    val approvedBy: String? = null,
    val createdBy: String? = null,
    val chargeMillis: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED",
    val lastSyncError: String? = null,
    val lastSyncedAt: Long? = null,
    val revision: Long = 0,
    val baseRevision: Long = 0,
    val updatedByUid: String? = null
)

object BookingAccountingChargeType {
    const val SERVICE_CHARGE = "SERVICE_CHARGE"
    const val DAMAGE_CHARGE = "DAMAGE_CHARGE"
    const val DISCOUNT = "DISCOUNT"

    val chargeTypes = setOf(SERVICE_CHARGE, DAMAGE_CHARGE, DISCOUNT)

    fun normalize(value: String?): String {
        return when (value?.trim()?.uppercase()) {
            DAMAGE_CHARGE -> DAMAGE_CHARGE
            DISCOUNT -> DISCOUNT
            else -> SERVICE_CHARGE
        }
    }
}

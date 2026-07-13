package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class BookingChangeSet(
    val bookingRemoteId: String,
    val create: Boolean,
    val setFields: Map<String, Any?>,
    val addRoomRemoteIds: List<String>,
    val removeRoomRemoteIds: List<String>,
    val rebuildFinancialLines: Boolean,
    val financialLineTemplate: Map<String, Any?>?,
    val financialLineRemoteIdsByKey: Map<String, String>
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "bookingRemoteId" to bookingRemoteId,
        "create" to create,
        "setFields" to setFields,
        "addRoomRemoteIds" to addRoomRemoteIds,
        "removeRoomRemoteIds" to removeRoomRemoteIds,
        "rebuildFinancialLines" to rebuildFinancialLines,
        "financialLineTemplate" to financialLineTemplate,
        "financialLineRemoteIdsByKey" to financialLineRemoteIdsByKey
    )

    fun toJson(): String = Gson().toJson(toMap())

    companion object {
        fun create(
            previous: BookingEntity?,
            requested: BookingEntity,
            previousLines: List<BookingFinancialLineEntity>,
            requestedLines: List<BookingFinancialLineEntity>
        ): BookingChangeSet {
            val create = previous == null
            val fields = linkedMapOf<String, Any?>()
            fun changed(name: String, old: Any?, new: Any?) {
                if (create || old != new) fields[name] = new
            }

            changed("bookingUuid", previous?.bookingUuid, requested.bookingUuid)
            changed("propertyRemoteId", previous?.propertyRemoteId, requested.propertyRemoteId)
            changed("guestName", previous?.guestName, requested.guestName)
            changed("guestMobile", previous?.guestMobile, requested.guestMobile)
            changed("sourceName", previous?.sourceName, requested.sourceName)
            changed("sourceRemoteId", previous?.sourceRemoteId, requested.sourceRemoteId)
            changed("sourceType", previous?.sourceType, requested.sourceType)
            changed("adultCount", previous?.adultCount, requested.adultCount)
            changed("childCount", previous?.childCount, requested.childCount)
            changed("checkInMillis", previous?.checkInMillis, requested.checkInMillis)
            changed("checkOutMillis", previous?.checkOutMillis, requested.checkOutMillis)
            changed("pricingStatus", previous?.pricingStatus, requested.pricingStatus)
            changed("bookingStatus", previous?.bookingStatus, requested.bookingStatus)
            changed("cancelledAt", previous?.cancelledAt, requested.cancelledAt)
            changed("cancellationReason", previous?.cancellationReason, requested.cancellationReason)
            changed("notes", previous?.notes, requested.notes)
            changed("grossCharges", previous?.grossCharges, requested.grossCharges)

            val oldRooms = previous?.roomRemoteIds.orEmpty().toSet()
            val newRooms = requested.roomRemoteIds.toSet()
            val lineShape = { lines: List<BookingFinancialLineEntity> ->
                lines.filter { !it.isDeleted }
                    .map { Triple(it.roomRemoteId, it.businessDateMillis, it.grossAmount) }
                    .toSet()
            }
            val rebuild = create ||
                oldRooms != newRooms ||
                previous?.checkInMillis != requested.checkInMillis ||
                previous?.checkOutMillis != requested.checkOutMillis ||
                previous?.grossCharges != requested.grossCharges ||
                lineShape(previousLines) != lineShape(requestedLines)
            val template = requestedLines.firstOrNull { !it.isDeleted }?.let { line ->
                mapOf(
                    "gstRatePercent" to line.gstRatePercent,
                    "hsnSacCode" to line.hsnSacCode,
                    "slabRemoteId" to line.slabRemoteId,
                    "slabName" to line.slabName,
                    "cgstRatePercent" to line.cgstRatePercent,
                    "sgstRatePercent" to line.sgstRatePercent,
                    "cessRatePercent" to line.cessRatePercent,
                    "source" to line.source
                )
            }
            return BookingChangeSet(
                bookingRemoteId = requested.remoteId,
                create = create,
                setFields = fields,
                addRoomRemoteIds = (newRooms - oldRooms).sorted(),
                removeRoomRemoteIds = (oldRooms - newRooms).sorted(),
                rebuildFinancialLines = rebuild,
                financialLineTemplate = template,
                financialLineRemoteIdsByKey = requestedLines
                    .filter { !it.isDeleted }
                    .associate { "${it.roomRemoteId}|${it.businessDateMillis}" to it.remoteId }
            )
        }

        fun fromJson(json: String): BookingChangeSet {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = Gson().fromJson(json, type)
            @Suppress("UNCHECKED_CAST")
            return BookingChangeSet(
                bookingRemoteId = map["bookingRemoteId"].toString(),
                create = map["create"] as? Boolean ?: false,
                setFields = map["setFields"] as? Map<String, Any?> ?: emptyMap(),
                addRoomRemoteIds = (map["addRoomRemoteIds"] as? List<*>)?.map { it.toString() }.orEmpty(),
                removeRoomRemoteIds = (map["removeRoomRemoteIds"] as? List<*>)?.map { it.toString() }.orEmpty(),
                rebuildFinancialLines = map["rebuildFinancialLines"] as? Boolean ?: false,
                financialLineTemplate = map["financialLineTemplate"] as? Map<String, Any?>,
                financialLineRemoteIdsByKey = (map["financialLineRemoteIdsByKey"] as? Map<*, *>)
                    ?.mapNotNull { (key, value) ->
                        val cleanKey = key?.toString() ?: return@mapNotNull null
                        val cleanValue = value?.toString() ?: return@mapNotNull null
                        cleanKey to cleanValue
                    }?.toMap().orEmpty()
            )
        }
    }
}

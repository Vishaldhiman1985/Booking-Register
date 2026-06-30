package com.example.bookingregister.ui.food

import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity

class FoodBillHeaderResolver {

    data class BillHeaderInfo(
        val name: String,
        val address: String,
        val gstin: String,
        val phone: String
    )

    fun resolve(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>,
        rooms: List<RoomEntity>,
        managedProperties: List<ManagedPropertyEntity>,
        hotelProfile: HotelEntity?
    ): BillHeaderInfo {
        val propertyRemoteId = bill.propertyRemoteId
            ?: items.mapNotNull { item ->
                rooms.firstOrNull { room -> room.roomName == item.roomName }?.propertyRemoteId
            }.distinct().singleOrNull()

        val property = propertyRemoteId?.let { id ->
            managedProperties.firstOrNull { it.remoteId == id }
        }

        val hotel = hotelProfile

        return BillHeaderInfo(
            name = bill.supplierName
                ?: property?.legalName
                ?: property?.propertyName
                ?: hotel?.hotelName
                ?: "Hotel",
            address = bill.supplierAddress
                ?: property?.address
                ?: hotel?.address
                ?: "",
            gstin = bill.supplierGstin
                ?: property?.gstNumber
                ?: "",
            phone = bill.supplierPhone
                ?: property?.phone
                ?: hotel?.phone
                ?: ""
        )
    }
}

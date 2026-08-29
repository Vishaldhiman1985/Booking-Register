package com.example.bookingregister.reporting.property

data class PropertyOccupancyFacts(
    val occupancyPercent: Int,
    val occupiedRoomNights: Int,
    val availableRoomNights: Int,
    val averageOccupiedRoomsPerDay: Double,
    val roomCount: Int
)

package com.example.bookingregister.ui.food

import com.example.bookingregister.data.entities.FoodOrderEntity

data class ActiveFoodOrderGroupUiModel(
    val propertyName: String,
    val roomName: String,
    val roomTotal: Double,
    val orders: List<ActiveFoodOrderUiModel>
)

data class ActiveFoodOrderUiModel(
    val order: FoodOrderEntity,
    val itemCount: Int,
    val isSelected: Boolean,
    val canSelect: Boolean
)

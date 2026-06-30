package com.example.bookingregister.property.domain

object RoomCategoryPalette {
    const val NO_CATEGORY_LABEL = "No Category"
    const val DEFAULT_CATEGORY = ""
    const val DEFAULT_COLOR = "#EEF0F2"

    val colors = listOf(
        CategoryColor("Light Grey", "#EEF0F2"),
        CategoryColor("Soft Orange", "#F8DEC7"),
        CategoryColor("Warm Sand", "#EFE4D2"),
        CategoryColor("Mist Blue", "#DDEAF3"),
        CategoryColor("Pale Teal", "#DCEDEA"),
        CategoryColor("Soft Lilac", "#E8E1F0"),
        CategoryColor("Dusty Rose", "#F1DFE3")
    )

    fun colorForName(name: String): String {
        return colors.firstOrNull { it.name == name }?.hex ?: DEFAULT_COLOR
    }
}

data class CategoryColor(
    val name: String,
    val hex: String
)
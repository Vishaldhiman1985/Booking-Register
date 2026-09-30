package com.example.bookingregister.booking.domain

object BookingRoomPlanIdentity {

    fun documentId(
        bookingRemoteId: String,
        businessDateMillis: Long,
        roomRemoteId: String
    ): String {
        require(bookingRemoteId.isNotBlank()) {
            "Booking remote ID cannot be blank."
        }
        require(businessDateMillis > 0L) {
            "Business date must be positive."
        }
        require(roomRemoteId.isNotBlank()) {
            "Room remote ID cannot be blank."
        }

        return "room_plan_${encodeURIComponent(bookingRemoteId)}_${businessDateMillis}_${encodeURIComponent(roomRemoteId)}"
    }

    private fun encodeURIComponent(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)

        return buildString {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                val char = unsigned.toChar()

                if (
                    char in 'A'..'Z' ||
                    char in 'a'..'z' ||
                    char in '0'..'9' ||
                    char == '-' ||
                    char == '_' ||
                    char == '.' ||
                    char == '!' ||
                    char == '~' ||
                    char == '*' ||
                    char == '\'' ||
                    char == '(' ||
                    char == ')'
                ) {
                    append(char)
                } else {
                    append('%')
                    append(HEX[unsigned ushr 4])
                    append(HEX[unsigned and 0x0F])
                }
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
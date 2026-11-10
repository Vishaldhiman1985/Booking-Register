package com.example.bookingregister.finance.dev

/** Pure quantity reconciliation. All quantities stay in the unit of the original PO line. */
object HotelPurchaseSimpleDeliveryPolicy {
    private const val EPS = 0.000001

    fun outstanding(ordered: Double, previouslyReceived: Double): Double {
        require(ordered.isFinite() && ordered > 0) { "Invalid ordered quantity" }
        require(previouslyReceived.isFinite() && previouslyReceived >= 0 && previouslyReceived <= ordered + EPS) {
            "Invalid earlier delivery"
        }
        return (ordered - previouslyReceived).coerceAtLeast(0.0)
    }

    fun validateUpdate(outstanding: Double, receivedNow: Double, unavailable: Double): Boolean {
        require(outstanding.isFinite() && outstanding >= 0) { "Invalid outstanding quantity" }
        require(receivedNow.isFinite() && receivedNow >= 0 && receivedNow <= outstanding + EPS) {
            "Received quantity cannot exceed outstanding"
        }
        require(unavailable.isFinite() && unavailable >= 0 && unavailable <= outstanding + EPS) {
            "Invalid unavailable quantity"
        }
        require(receivedNow + unavailable <= outstanding + EPS) { "Item quantity counted twice" }
        return kotlin.math.abs(outstanding - receivedNow - unavailable) < EPS
    }
}

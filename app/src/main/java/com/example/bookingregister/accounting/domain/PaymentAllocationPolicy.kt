package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import kotlin.math.min

data class ChargeBuckets(
    val stay: Double = 0.0,
    val food: Double = 0.0,
    val service: Double = 0.0
) {
    val total: Double get() = stay + food + service
}

data class PaymentAllocation(
    val selectedCategory: String,
    val stayAmount: Double,
    val foodAmount: Double,
    val serviceAmount: Double,
    val unappliedAmount: Double
) {
    val appliedTotal: Double get() = stayAmount + foodAmount + serviceAmount
}

object PaymentAllocationPolicy {
    fun allocate(
        amount: Double,
        selectedCategory: String?,
        charges: ChargeBuckets,
        alreadyPaid: ChargeBuckets = ChargeBuckets()
    ): PaymentAllocation {
        val category = BookingPaymentCategory.normalize(selectedCategory)
        var remaining = amount.coerceAtLeast(0.0)
        var stay = 0.0
        var food = 0.0
        var service = 0.0

        fun applyTo(openBalance: Double): Double {
            if (remaining <= 0.0) return 0.0
            val applied = min(remaining, openBalance.coerceAtLeast(0.0))
            remaining -= applied
            return applied
        }

        val stayBalance = charges.stay - alreadyPaid.stay
        val foodBalance = charges.food - alreadyPaid.food
        val serviceBalance = charges.service - alreadyPaid.service

        fun applyStay() {
            stay += applyTo(stayBalance - stay)
        }

        fun applyFood() {
            food += applyTo(foodBalance - food)
        }

        fun applyService() {
            service += applyTo(serviceBalance - service)
        }

        when (category) {
            BookingPaymentCategory.STAY -> {
                applyStay()
            }
            BookingPaymentCategory.FOOD -> {
                applyFood()
                applyService()
            }
            BookingPaymentCategory.SERVICE -> {
                applyService()
                applyFood()
            }
            BookingPaymentCategory.DAMAGE -> Unit
            else -> {
                applyStay()
                applyFood()
                applyService()
            }
        }

        return PaymentAllocation(
            selectedCategory = category,
            stayAmount = stay,
            foodAmount = food,
            serviceAmount = service,
            unappliedAmount = remaining.coerceAtLeast(0.0)
        )
    }
}

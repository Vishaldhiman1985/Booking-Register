package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import kotlin.math.abs

object FoodOrderIntegrity {
    private const val TOLERANCE = 0.02

    fun validate(order: FoodOrderEntity, items: List<FoodOrderItemEntity>): List<String> {
        val active = items.filter { !it.isDeleted && !it.isCancelled && it.orderRemoteId == order.remoteId }
        if (active.isEmpty()) return listOf("Food order ${order.orderNumber ?: order.remoteId} has no authoritative items")

        val itemSubtotal = active.sumOf { item ->
            item.lineSubtotal.takeIf { it > 0.0 } ?: (item.quantity * item.unitPrice)
        }
        val itemTotal = active.sumOf { item ->
            item.lineTotal.takeIf { it > 0.0 } ?: item.lineSubtotal.takeIf { it > 0.0 }
            ?: (item.quantity * item.unitPrice)
        }
        val errors = mutableListOf<String>()
        if (abs(order.subtotal - itemSubtotal) > TOLERANCE) errors += "Food order subtotal differs from items"
        if (abs(order.totalAmount - itemTotal) > TOLERANCE) errors += "Food order total differs from items"
        return errors
    }
}

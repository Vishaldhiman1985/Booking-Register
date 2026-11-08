package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HotelPurchaseOrderStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "finance_dev_purchase_orders_v1",
        Context.MODE_PRIVATE
    )

    fun all(): List<HotelPurchaseOrder> {
        val array = JSONArray(prefs.getString("orders", "[]") ?: "[]")

        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val linesArray = obj.getJSONArray("lines")

            HotelPurchaseOrder(
                id = obj.getString("id"),
                vendorId = obj.getString("vendorId"),
                vendorName = obj.getString("vendorName"),
                createdAtMillis = obj.getLong("createdAtMillis"),
                status = obj.optString("status", "DRAFT"),
                lines = (0 until linesArray.length()).map { i ->
                    val line = linesArray.getJSONObject(i)
                    HotelPurchaseOrderLine(
                        itemId = line.getString("itemId"),
                        itemName = line.getString("itemName"),
                        quantity = line.getDouble("quantity"),
                        unit = line.getString("unit")
                    )
                }
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    fun save(order: HotelPurchaseOrder) {
        HotelPurchaseOrderPolicy.validate(order)

        val orders = all().filterNot { it.id == order.id } + order
        val array = JSONArray()

        orders.forEach { entry ->
            val lines = JSONArray()
            entry.lines.forEach { line ->
                lines.put(JSONObject().apply {
                    put("itemId", line.itemId)
                    put("itemName", line.itemName)
                    put("quantity", line.quantity)
                    put("unit", line.unit)
                })
            }

            array.put(JSONObject().apply {
                put("id", entry.id)
                put("vendorId", entry.vendorId)
                put("vendorName", entry.vendorName)
                put("createdAtMillis", entry.createdAtMillis)
                put("status", entry.status)
                put("lines", lines)
            })
        }

        check(
            prefs.edit().putString("orders", array.toString()).commit()
        ) {
            "Unable to save purchase order"
        }
    }
}

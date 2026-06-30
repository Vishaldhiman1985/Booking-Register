package com.example.bookingregister.ui.food

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.example.bookingregister.data.entities.FoodBillEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchivedFoodBillsRenderer(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val money: (Double) -> String,
    private val rounded: (Int, Int, Float) -> android.graphics.drawable.Drawable,
    private val propertyNameForBill: (FoodBillEntity) -> String,
    private val onBillClicked: (FoodBillEntity) -> Unit
) {

    fun render(
        container: LinearLayout,
        bills: List<FoodBillEntity>
    ) {
        bills
            .groupBy { propertyNameForBill(it).ifBlank { "Property" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (propertyName, propertyBills) ->
                container.addView(TextView(context).apply {
                    text = propertyName
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#128C7E"))
                    setPadding(dp(4), dp(14), dp(4), dp(8))
                })
                propertyBills.sortedByDescending { it.billMillis }.forEach { bill ->
                    renderBill(container, bill)
                }
            }
    }

    private fun renderBill(
        container: LinearLayout,
        bill: FoodBillEntity
    ) {
        val green = Color.parseColor("#128C7E")

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, Color.rgb(222, 235, 232), dp(18).toFloat())
            setOnClickListener { onBillClicked(bill) }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        topRow.addView(TextView(context).apply {
            text = if (bill.isConsolidatedGuestBill()) "${bill.billNumber}  Consolidated" else bill.billNumber
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 32))
        }, LinearLayout.LayoutParams(0, -2, 1f))

        topRow.addView(TextView(context).apply {
            text = "₹${money(bill.grandTotal)}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        })

        card.addView(topRow)

        bill.guestName?.takeIf { it.isNotBlank() }?.let { guestName ->
            card.addView(TextView(context).apply {
                text = "Guest: $guestName"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(green)
                setPadding(0, dp(6), 0, 0)
            })
        }

        card.addView(TextView(context).apply {
            text = if (bill.isConsolidatedGuestBill()) "Guest folio • ${bill.roomsIncluded}" else bill.roomsIncluded
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                .format(Date(bill.billMillis))
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(3), 0, 0)
        })

        container.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(12))
        })
    }
}
private fun FoodBillEntity.isConsolidatedGuestBill(): Boolean = remoteId.contains("_final_bill_")

package com.example.bookingregister.ui.food

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.bookingregister.data.entities.FoodOrderEntity

class SelectedFoodOrderFooterRenderer(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val money: (Double) -> String,
    private val rounded: (Int, Int, Float) -> android.graphics.drawable.Drawable,
    private val onPreviewOrders: (List<FoodOrderEntity>) -> Unit,
    private val onGenerateBill: (List<FoodOrderEntity>) -> Unit
) {

    fun render(
        container: LinearLayout,
        orders: List<FoodOrderEntity>,
        selectedOrderIds: Set<String>
    ) {
        val selectedOrders = orders.filter { selectedOrderIds.contains(it.remoteId) }
        if (selectedOrders.isEmpty()) return

        val total = selectedOrders.sumOf { it.totalAmount }

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, Color.rgb(222, 229, 224), dp(16).toFloat())
        }

        footer.addView(TextView(context).apply {
            text = "${selectedOrders.size} Order${if (selectedOrders.size == 1) "" else "s"} Selected"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 32))
        })

        footer.addView(TextView(context).apply {
            text = "Total Amount: ₹${money(total)}"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#128C7E"))
            setPadding(0, dp(4), 0, dp(12))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(secondaryButton("Preview Orders") {
            onPreviewOrders(selectedOrders)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            setMargins(0, 0, dp(10), 0)
        })

        actions.addView(primaryButton("Generate Bill") {
            onGenerateBill(selectedOrders)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))

        footer.addView(actions)

        container.addView(footer, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(4), 0, dp(16))
        })
    }

    private fun primaryButton(textValue: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = textValue
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#128C7E"), Color.TRANSPARENT, dp(14).toFloat())
            setOnClickListener { action() }
        }
    }

    private fun secondaryButton(textValue: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = textValue
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#128C7E"))
            background = rounded(Color.parseColor("#EAFBF8"), Color.rgb(214, 235, 224), dp(14).toFloat())
            setOnClickListener { action() }
        }
    }
}
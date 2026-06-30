package com.example.bookingregister.ui.food

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.example.bookingregister.data.entities.FoodOrderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActiveFoodOrdersRenderer(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val money: (Double) -> String,
    private val rounded: (Int, Int, Float) -> android.graphics.drawable.Drawable,
    private val onOrderClicked: (FoodOrderEntity) -> Unit,
    private val onSelectionChanged: (String, Boolean) -> Unit
) {

    fun renderGroups(
        container: LinearLayout,
        groups: List<ActiveFoodOrderGroupUiModel>
    ) {
        groups.groupBy { it.propertyName }.forEach { (propertyName, propertyGroups) ->
            container.addView(TextView(context).apply {
                text = propertyName
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(20, 24, 32))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            propertyGroups.forEach { group ->
                renderGroup(container, group)
            }
        }
    }

    private fun renderGroup(
        container: LinearLayout,
        group: ActiveFoodOrderGroupUiModel
    ) {
        val green = Color.parseColor("#128C7E")
        val greenSoft = Color.parseColor("#EAFBF8")

        val roomCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(8))
            background = rounded(Color.WHITE, Color.rgb(222, 229, 224), dp(16).toFloat())
        }

        val roomHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        roomHeader.addView(TextView(context).apply {
            text = group.roomName
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        }, LinearLayout.LayoutParams(0, -2, 1f))

        roomHeader.addView(TextView(context).apply {
            text = "${group.orders.size} Order${if (group.orders.size == 1) "" else "s"}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(greenSoft, Color.TRANSPARENT, dp(20).toFloat())
        })

        roomHeader.addView(TextView(context).apply {
            text = "₹${money(group.roomTotal)}"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 32))
            setPadding(dp(12), 0, 0, 0)
        })

        roomCard.addView(roomHeader)

        group.orders.forEach { orderUi ->
            val order = orderUi.order

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(12))
                setOnClickListener { onOrderClicked(order) }
            }

            if (orderUi.canSelect) {
                row.addView(CheckBox(context).apply {
                    isChecked = orderUi.isSelected
                    setOnCheckedChangeListener { _, checked ->
                        onSelectionChanged(order.remoteId, checked)
                    }
                })
            } else {
                row.addView(TextView(context).apply {
                    text = "Room"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#5B2CF1"))
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = rounded(Color.parseColor("#F2EEFF"), Color.TRANSPARENT, dp(12).toFloat())
                }, LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(0, 0, dp(10), 0)
                })
            }

            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL

                addView(TextView(context).apply {
                    text = order.orderNumber ?: "Food Order"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(35, 35, 35))
                })

                addView(TextView(context).apply {
                    text = "${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.orderMillis))}  •  ${orderUi.itemCount} item${if (orderUi.itemCount == 1) "" else "s"}"
                    textSize = 12f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(3), 0, 0)
                })

            }, LinearLayout.LayoutParams(0, -2, 1f))

            row.addView(TextView(context).apply {
                text = "₹${money(order.totalAmount)}"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(20, 24, 32))
            })

            roomCard.addView(row)
        }

        container.addView(roomCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(14))
        })
    }
}

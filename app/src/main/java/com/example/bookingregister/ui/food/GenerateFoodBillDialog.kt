package com.example.bookingregister.ui.food

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.bookingregister.data.entities.FoodOrderEntity

class GenerateFoodBillDialog(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val onGenerate: (
        selectedOrders: List<FoodOrderEntity>,
        guestName: String,
        guestMobile: String,
        guestAddress: String,
        guestGstin: String,
        paymentMode: String,
        discountAmount: Double,
        withGst: Boolean
    ) -> Unit
) {

    fun show(selectedOrders: List<FoodOrderEntity>) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(12), dp(36), 0)
        }

        val guestNameInput = input("Guest name optional", null)
        val mobileInput = input("Mobile optional", null, numeric = true)
        val gstinInput = input("Guest GSTIN optional", null)
        val addressInput = input("Address optional", null, singleLine = false)
        val paymentModeInput = input("Payment mode", "Cash")
        val discountInput = input("Discount", "0", numeric = true)

        val withGst = CheckBox(context).apply {
            text = "Generate GST food invoice"
            isChecked = true
        }

        listOf(
            guestNameInput,
            mobileInput,
            gstinInput,
            addressInput,
            paymentModeInput,
            discountInput,
            withGst
        ).forEach { layout.addView(it) }

        layout.addView(TextView(context).apply {
            text = "Selected orders will move from Active Orders to Bills after bill generation."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(10), 0, 0)
        })

        AlertDialog.Builder(context)
            .setTitle("Generate Food Bill")
            .setView(layout)
            .setPositiveButton("Generate") { _, _ ->
                onGenerate(
                    selectedOrders,
                    guestNameInput.text.toString(),
                    mobileInput.text.toString(),
                    addressInput.text.toString(),
                    gstinInput.text.toString(),
                    paymentModeInput.text.toString(),
                    discountInput.text.toString().toDoubleOrNull() ?: 0.0,
                    withGst.isChecked
                )
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun input(
        hintText: String,
        value: String?,
        numeric: Boolean = false,
        singleLine: Boolean = true
    ): EditText {
        return EditText(context).apply {
            hint = hintText
            setText(value.orEmpty())
            setSingleLine(singleLine)
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
        }
    }
}

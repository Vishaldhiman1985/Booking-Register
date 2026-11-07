package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp

class FinanceDevSafetyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        content.addView(text("FINANCE DEV", 28f, Color.rgb(180, 25, 45), true))
        content.addView(text("Safe development shell", 20f, Color.DKGRAY, true).withTopMargin(10))
        content.addView(
            text(
                "Package\n${packageName}\n\n" +
                    "Firebase configuration\n$projectId (verified staging)\n\n" +
                    "Cloud access\nLOCKED during Phase 0\n\n" +
                    "Local storage\nIsolated from Booking Register production",
                16f,
                Color.rgb(45, 55, 70),
                false
            ).withTopMargin(32)
        )
        content.addView(
            text(
                "The shared staging dataset will not be opened until a dedicated Finance " +
                    "tenant or namespace and server-side permissions are approved.",
                14f,
                Color.rgb(85, 95, 110),
                false
            ).withTopMargin(28)
        )

        setContentView(content)
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun TextView.withTopMargin(marginDp: Int): TextView = apply {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = dp(marginDp)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

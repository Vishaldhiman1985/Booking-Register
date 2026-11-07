package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Finance DEV offline preview. The production launcher and existing database are unchanged. */
class FinanceDevSafetyActivity : AppCompatActivity() {
    private val tabs = listOf("Dashboard", "Expenses", "Payments", "Vendors", "Reports")
    private var currentTab = "Dashboard"
    private val pageBackgroundColor = Color.rgb(247, 248, 250)
    private val ink = Color.rgb(32, 42, 56)
    private val muted = Color.rgb(94, 106, 120)
    private val accent = Color.rgb(180, 25, 45)

    private data class ScreenSnapshot(
        val drafts: List<FinanceDemoDraftBill>,
        val count: Long,
        val totalDraftPaise: Long
    )

    private val localDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            FinanceDemoDraftDatabase::class.java,
            "finance_dev_synthetic_drafts_v1.db"
        ).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTab = savedInstanceState?.getString("finance_dev_tab")
            ?.takeIf { it in tabs } ?: "Dashboard"
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("finance_dev_tab", currentTab)
        super.onSaveInstanceState(outState)
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val dao = localDatabase.drafts()
                    ScreenSnapshot(
                        drafts = dao.recent(FinanceDemoDraftPolicy.TENANT, FinanceDemoDraftPolicy.PROPERTY),
                        count = dao.count(FinanceDemoDraftPolicy.TENANT, FinanceDemoDraftPolicy.PROPERTY),
                        totalDraftPaise = dao.totalDraftPaise(FinanceDemoDraftPolicy.TENANT, FinanceDemoDraftPolicy.PROPERTY)
                    )
                }
                // Fail closed even for local preview data; never display foreign-scope rows.
                snapshot.drafts.forEach(FinanceDemoDraftPolicy::validate)
                showScreen(snapshot)
            } catch (error: Exception) {
                showError(error)
            }
        }
    }

    private fun showScreen(snapshot: ScreenSnapshot) {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(24))
            setBackgroundColor(pageBackgroundColor)
        }
        page.addView(label("FINANCE DEV", 27f, accent, true))
        page.addView(label("Booking Register  •  Separate offline test app", 13f, muted, false))
        page.addView(space(14))
        page.addView(panel().apply {
            addView(label("SYNTHETIC DATA ONLY  •  NOT POSTED", 13f, accent, true))
            addView(label("Cloud access: LOCKED  |  Property: Demo Property 01", 12f, ink, false))
            addView(label("Firebase config: ${FirebaseApp.getInstance().options.projectId.orEmpty()} (network disabled)", 11f, muted, false))
            addView(label("Real invoices, vendors and guest information are prohibited in this preview.", 12f, muted, false))
        })
        page.addView(space(14))
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.forEach { tab ->
            navRow.addView(Button(this).apply {
                text = tab
                textSize = 11f
                isAllCaps = false
                minHeight = dp(45)
                setTextColor(if (currentTab == tab) Color.WHITE else ink)
                background = rounded(if (currentTab == tab) accent else Color.WHITE, 12f)
                setOnClickListener { currentTab = tab; refresh() }
            })
            navRow.addView(spaceHorizontal(5))
        }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(navRow)
        })
        page.addView(space(20))
        when (currentTab) {
            "Dashboard" -> dashboard(page, snapshot)
            "Expenses" -> expenses(page, snapshot.drafts)
            "Payments" -> unavailable(page, "Payments", "Payment posting, allocations and banking are not enabled. No payment has been made.")
            "Vendors" -> unavailable(page, "Vendors", "Vendor masters, payables and advances are not enabled. Demo names exist only inside sample drafts.")
            "Reports" -> unavailable(page, "Reports", "Financial statements must be based on server-posted, balanced journals. Drafts cannot be treated as profit or tax liabilities.")
        }
        page.addView(space(20))
        page.addView(label("Local preview database only. No internet permission. No production data or posting.", 12f, muted, false))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        })
    }

    private fun dashboard(page: LinearLayout, snapshot: ScreenSnapshot) {
        page.addView(label("Dashboard", 23f, ink, true))
        page.addView(label("Demo Property 01  •  November 2026", 13f, muted, false))
        page.addView(space(14))
        page.addView(panel().apply {
            addView(label("Synthetic draft bills — NOT EXPENSES POSTED", 12f, muted, true))
            addView(space(8))
            addView(label(FinanceDemoDraftPolicy.moneyText(snapshot.totalDraftPaise), 28f, ink, true))
            addView(label("${snapshot.count} local example drafts. No accounts affected.", 12f, muted, false))
        })
        page.addView(space(10))
        page.addView(panel().apply {
            addView(label("Live revenue", 15f, ink, true))
            addView(label("Not connected", 17f, accent, true))
            addView(label("Profit, bank balance and GST remain unavailable until approved server ledger integration.", 12f, muted, false))
        })
    }

    private fun expenses(page: LinearLayout, drafts: List<FinanceDemoDraftBill>) {
        page.addView(label("Expenses", 23f, ink, true))
        page.addView(label("Local fictional examples only. These are not bills recorded in a ledger.", 13f, muted, false))
        page.addView(space(12))
        page.addView(Button(this).apply {
            text = "Add synthetic example draft"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(accent, 10f)
            setOnClickListener { chooseFixture() }
        })
        if (drafts.isEmpty()) {
            page.addView(space(12))
            page.addView(panel().apply { addView(label("No example drafts yet. Add a fictional sample to test offline persistence.", 14f, muted, false)) })
        } else drafts.forEach { draft ->
            page.addView(space(10))
            page.addView(panel().apply {
                addView(label(draft.category, 17f, ink, true))
                addView(label(FinanceDemoDraftPolicy.moneyText(draft.amountPaise), 20f, ink, true))
                addView(label("Vendor: ${FinanceDemoDraftPolicy.fixtures.first { it.code == draft.vendorCode }.vendor}", 12f, muted, false))
                addView(label("Month: ${draft.accountingPeriod}  |  LOCAL DRAFT — NOT POSTED", 12f, accent, true))
            })
        }
    }

    private fun unavailable(page: LinearLayout, title: String, description: String) {
        page.addView(label(title, 23f, ink, true))
        page.addView(space(12))
        page.addView(panel().apply {
            addView(label("Not yet implemented", 17f, accent, true))
            addView(space(8))
            addView(label(description, 14f, muted, false))
        })
    }

    private fun chooseFixture() {
        val fixtures = FinanceDemoDraftPolicy.fixtures
        AlertDialog.Builder(this)
            .setTitle("Choose a fictional sample (NOT POSTED)")

            .setItems(fixtures.map { "${it.category} — ${FinanceDemoDraftPolicy.moneyText(it.paise)}" }.toTypedArray()) { _, index ->
                lifecycleScope.launch {
                    try {
                        val draft = FinanceDemoDraftPolicy.create(
                            code = fixtures[index].code,
                            id = UUID.randomUUID().toString(),
                            nowMillis = System.currentTimeMillis()
                        )
                        FinanceDemoDraftPolicy.validate(draft)
                        withContext(Dispatchers.IO) { localDatabase.drafts().insert(draft) }
                        refresh()
                    } catch (error: Exception) {
                        showError(error)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(error: Exception) {
        Toast.makeText(this, "Finance DEV local preview error. No posting occurred.", Toast.LENGTH_LONG).show()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(pageBackgroundColor)
        }
        page.addView(label("FINANCE DEV — PREVIEW STOPPED", 22f, accent, true))
        page.addView(label("Local draft data could not be validated or loaded. No data was posted.", 15f, ink, false))
        page.addView(label("Error category: ${error.javaClass.simpleName}", 12f, muted, false))
        setContentView(page)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.WHITE, 15f)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun space(height: Int) = TextView(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun spaceHorizontal(width: Int) = TextView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}

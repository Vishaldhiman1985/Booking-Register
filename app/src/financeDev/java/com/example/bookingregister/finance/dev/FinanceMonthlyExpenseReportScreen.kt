package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Local Finance DEV monthly expense register. This is NOT a final taxable P/L. */
internal class FinanceMonthlyExpenseReportScreen(private val activity: AppCompatActivity) {
    private val ink=Color.rgb(29,44,65)
    private val muted=Color.rgb(105,117,132)
    private val green=Color.rgb(0,115,78)
    private val red=Color.rgb(171,60,68)
    private val pale=Color.rgb(246,248,251)
    private val divider=Color.rgb(230,234,239)
    private val fmt=DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private fun dp(i:Int)=(i*activity.resources.displayMetrics.density+.5f).toInt()
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun text(t:String,size:Float=13f,bold:Boolean=false,color:Int=ink)=TextView(activity).apply {
        this.text=t;textSize=size;setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun money(p:Long)="₹"+NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits=2;minimumFractionDigits=0
    }.format(BigDecimal.valueOf(p,2))
    private fun shape(fill:Int):GradientDrawable=GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(13).toFloat();setStroke(dp(1),divider)
    }
    private fun rule(parent:LinearLayout){ parent.addView(View(activity).apply{setBackgroundColor(divider)},LinearLayout.LayoutParams(-1,dp(1))) }
    private fun panel()=column().apply{background=shape(Color.WHITE);setPadding(dp(14),dp(14),dp(14),dp(14))}

    private fun ledgerLines(drafts:List<FinanceDemoDraftBill>):List<FinanceMonthlyExpenseReportPolicy.Expense> {
        val manual=drafts.associateBy { "manual:${it.draftId}" }
        val catalog=FinanceExpenseCatalogLoader.load(activity)
        val categoriesByCode=catalog.categories.associateBy { it.code }
        val fixed=FinanceFixedExpenseStore(activity).use { store ->
            store.issueThrough(YearMonth.now())
            val rules=store.rules().associateBy { it.id }
            store.bills().mapNotNull { bill -> rules[bill.ruleId]?.let { bill.id to it } }.toMap()
        }
        return FinanceActivityStore(activity).use { store ->
            store.synchronize(drafts)
            store.bills().map { bill ->
                val source=bill.source
                val draft=manual[bill.id]
                val fixedRule=fixed[bill.id]
                val mapped=draft?.expenseTypeCode?.let(catalog::findType)
                val category=when(source) {
                    "MANUAL"-> if(mapped?.first?.code == FinanceExpenseCategoryRules.STAFF_ONLY ||
                        draft?.expenseCategoryCode == FinanceExpenseCategoryRules.STAFF_ONLY)
                        "Needs review · historical manual staff expense"
                        else mapped?.first?.name ?: draft?.expenseCategoryCode?.let(categoriesByCode::get)?.name
                            ?: draft?.category?.takeIf{it.isNotBlank()} ?: "Needs classification"
                    "FIXED"->fixedRule?.category?.takeIf{it.isNotBlank()} ?: "Needs classification · recurring"
                    "PAYROLL"-> if(bill.title.startsWith("Salary ·")) "Salaries & Wages" else "Employee Benefits"
                    else->"Needs classification"
                }
                val type=when(source) {
                    "MANUAL"->mapped?.second?.name ?: draft?.expenseTypeLabel ?: bill.title
                    "FIXED"->fixedRule?.type ?: bill.title
                    "PAYROLL"->if(bill.title.startsWith("Salary ·")) "Monthly salary" else "Company benefit"
                    else->bill.title
                }
                FinanceMonthlyExpenseReportPolicy.Expense(
                    bill.id,bill.period,bill.department,category,type,bill.title,bill.vendor,
                    bill.amount,bill.paid,bill.due,source
                )
            }
        }
    }

    fun render(parent:LinearLayout,drafts:List<FinanceDemoDraftBill>) {
        var selected=YearMonth.now()
        parent.removeAllViews()
        val heading=panel().apply {
            background=shape(pale)
            addView(text("Monthly Expense Report",22f,true))
            addView(text("Categorised bills · salaries · recurring expenses",12f,false,muted))
        }
        parent.addView(heading)
        parent.addView(View(activity),LinearLayout.LayoutParams(1,dp(12)))
        val monthRow=row().apply{setPadding(dp(7),dp(8),dp(7),dp(8))}
        val previous=text("‹",30f,true,green).apply { gravity=Gravity.CENTER;contentDescription="Previous month" }
        val current=text("",16f,true).apply {gravity=Gravity.CENTER}
        val next=text("›",30f,true,green).apply{gravity=Gravity.CENTER;contentDescription="Next month"}
        monthRow.addView(previous,LinearLayout.LayoutParams(dp(46),dp(45)))
        monthRow.addView(current,LinearLayout.LayoutParams(0,dp(45),1f))
        monthRow.addView(next,LinearLayout.LayoutParams(dp(46),dp(45)))
        parent.addView(monthRow)
        val report=column()
        parent.addView(report)
        val entries=try { ledgerLines(drafts) } catch(e:Exception){
            report.addView(text("Unable to read category report: ${e.message ?: "Please review finance records"}",14f,true,red));emptyList()
        }
        fun display() {
            report.removeAllViews()
            current.text=selected.format(fmt)
            next.alpha=if(selected>=YearMonth.now())0.25f else 1f
            val list=FinanceMonthlyExpenseReportPolicy.monthly(entries,selected.toString())
            val totals=FinanceMonthlyExpenseReportPolicy.totals(list)
            report.addView(panel().apply {
                addView(text("RECORDED EXPENSES",11f,true,muted))
                addView(text(money(totals.gross),25f,true,ink))
                addView(text("Already settled ${money(totals.settled)}  ·  Outstanding ${money(totals.outstanding)}",12f,false,if(totals.outstanding>0)red else green))
                addView(text("${totals.count} distinct bills · ${selected.format(fmt)}",11f,false,muted))
            })
            report.addView(View(activity),LinearLayout.LayoutParams(1,dp(13)))
            if(list.isEmpty()) report.addView(text("No recorded expense bills for this month.",14f,false,muted))
            val grouped=FinanceMonthlyExpenseReportPolicy.categoryTotals(list)
            val depts=grouped.groupBy { it.department }
            depts.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (dept, cats) ->
                val departmentRows=list.filter { it.department.trim().ifEmpty { "Unassigned" }==dept }
                val total=FinanceMonthlyExpenseReportPolicy.totals(departmentRows)
                report.addView(text("$dept  ·  ${money(total.gross)}",16f,true,green).apply {
                    setPadding(dp(4),dp(14),dp(4),dp(9))
                })
                cats.forEach { cat ->
                    val cRows=departmentRows.filter { it.category.trim().ifEmpty { "Needs classification" }==cat.category }
                    report.addView(panel().apply {
                        val header=row()
                        header.addView(text(cat.category,14f,true),LinearLayout.LayoutParams(0,-2,1f))
                        header.addView(text(money(cat.totals.gross),14f,true),LinearLayout.LayoutParams(-2,-2))
                        addView(header)
                        addView(text("Settled ${money(cat.totals.settled)}  ·  Due ${money(cat.totals.outstanding)}",11f,false,muted))
                        addView(View(activity),LinearLayout.LayoutParams(1,dp(9)))
                        cRows.forEachIndexed { index, item ->
                            if(index>0)rule(this)
                            val line=row().apply {setPadding(0,dp(7),0,dp(7))}
                            line.addView(column().apply {
                                addView(text(item.type,12f,true))
                                addView(text("${item.payee} · ${item.source}",11f,false,muted))
                            },LinearLayout.LayoutParams(0,-2,1f))
                            line.addView(text(money(item.grossPaise),12f,true),LinearLayout.LayoutParams(-2,-2))
                            addView(line)
                        }
                    })
                    report.addView(View(activity),LinearLayout.LayoutParams(1,dp(8)))
                }
            }
            report.addView(text("Bill amounts are recorded liabilities, not cash expenditure. Salary advances reduce payable balances but not earned salary expense. Stock purchases are not inventory consumption; tax and stock valuation need separate CA review.",11f,false,muted).apply {
                setPadding(dp(3),dp(14),dp(3),dp(24))
            })
        }
        previous.setOnClickListener { selected=selected.minusMonths(1);display() }
        next.setOnClickListener { if(selected<YearMonth.now()){selected=selected.plusMonths(1);display()} }
        display()
    }
}

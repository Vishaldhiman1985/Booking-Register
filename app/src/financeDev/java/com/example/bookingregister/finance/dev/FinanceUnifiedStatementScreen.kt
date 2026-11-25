package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * V17 Finance DEV: dated bill/payment/advance/adjustment/revenue activity, shared local payables,
 * and vendor dues. Does not make bank transfers or statutory accounting entries.
 */
internal class FinanceUnifiedStatementScreen(
    private val activity:AppCompatActivity,
    private val onChanged:()->Unit
) {
    private val ink=Color.rgb(27,43,61)
    private val green=Color.rgb(7,111,75)
    private val red=Color.rgb(177,55,67)
    private val amber=Color.rgb(157,99,27)
    private var onUnpaidClick:()->Unit = {}
    fun withUnpaidAction(action:()->Unit):FinanceUnifiedStatementScreen {
        onUnpaidClick=action;return this
    }
    private val muted=Color.rgb(105,116,132)
    private val surface=Color.rgb(246,248,250)
    private val rule=Color.rgb(228,232,237)
    private val dayFormat=DateTimeFormatter.ofPattern("dd MMM",Locale.ENGLISH)
    private val monthFormat=DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)
    private var activityFilter="All"

    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun col()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun space(h:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun line()=View(activity).apply { setBackgroundColor(rule);layoutParams=LinearLayout.LayoutParams(-1,dp(1)) }
    private fun rounded(color:Int,radius:Int=12)=GradientDrawable().apply { setColor(color);cornerRadius=dp(radius).toFloat() }
    private fun txt(value:String,size:Float=13f,bold:Boolean=false,color:Int=ink)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun money(paise:Long):String = "₹"+NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        minimumFractionDigits=0;maximumFractionDigits=2
    }.format(BigDecimal.valueOf(paise,2))
    private fun toast(message:String)=Toast.makeText(activity,message,Toast.LENGTH_LONG).show()
    private fun edit(initial:String,hint:String,numeric:Boolean=false)=EditText(activity).apply {
        setText(initial);this.hint=hint;setSingleLine(true);textSize=16f
        if(numeric)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun field(parent:LinearLayout,name:String,control:View) {
        parent.addView(txt(name,12f,true,muted));parent.addView(space(4));parent.addView(control);parent.addView(space(12))
    }
    private fun spinner(values:List<String>)=Spinner(activity).apply {
        adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,values)
    }
    private fun loaded(drafts:List<FinanceDemoDraftBill>):FinanceActivityStore {
        val store=FinanceActivityStore(activity)
        try { store.synchronize(drafts) } catch(error:Exception) {
            store.close();throw error
        }
        return store
    }
    /** Section heading is rendered once in the main Finance toolbar. */
    private fun header(parent:LinearLayout,title:String,detail:String) {
        parent.addView(space(3))
    }
    /** Bill balances and declared rent taxes: same definitions as Dashboard. */
    private fun summary(parent:LinearLayout,total:FinanceActivityStore.Overview,
                        taxDue:Long?,stockCount:Int,onPendingStock:()->Unit) {
        fun cell(title:String,value:String,color:Int):LinearLayout=col().apply {
            setPadding(dp(10),dp(11),dp(9),dp(11));background=rounded(surface,13)
            addView(txt(title,11f,false,muted));addView(space(5))
            addView(txt(value,16f,true,color))
        }
        val top=row()
        listOf(Triple("Bills",money(total.billed),ink),
            Triple("Paid / settled",money(total.settled),green),
            Triple("Unpaid bills",money(total.due),red)).forEachIndexed { index,part ->
            if(index>0) top.addView(View(activity),LinearLayout.LayoutParams(dp(6),1))
            top.addView(cell(part.first,part.second,part.third).apply {
                if(index==2) { isClickable=true;isFocusable=true
                    contentDescription="Open bills due for payment"
                    setOnClickListener { onUnpaidClick() }
                }
            },LinearLayout.LayoutParams(0,-2,1f))
        }
        parent.addView(top);parent.addView(space(8))
        val dues=row()
        val combined = taxDue?.let { Math.addExact(total.due,it) }
        dues.addView(cell("Total outstanding",combined?.let(::money) ?: "Review",if((combined ?: 0L)>0)red else ink),
            LinearLayout.LayoutParams(0,-2,1f))
        dues.addView(View(activity),LinearLayout.LayoutParams(dp(8),1))
        dues.addView(cell("Rent GST / RCM  ›",taxDue?.let(::money) ?: "Review",amber).apply {
            isClickable=true;isFocusable=true
            contentDescription="Open declared rent GST and RCM obligations"
            setOnClickListener { FinanceLeaseTaxScreen(activity,onChanged).show() }
        },LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(dues);parent.addView(space(9))
        val links=row()
        links.addView(chip("Vendors · Advances  ›",false) { openLedgerHub() },
            LinearLayout.LayoutParams(0,-2,1f))
        links.addView(View(activity),LinearLayout.LayoutParams(dp(8),1))
        links.addView(chip("Quantities ($stockCount)  ›",false) { onPendingStock() },
            LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(links);parent.addView(space(6))
        parent.addView(txt("Bill period selected · current outstanding balances; rent tax is separate from expenses.",11f,false,muted))
        parent.addView(space(12))
    }
    private fun chip(name:String,active:Boolean,click:()->Unit):TextView=txt(name,12f,active,if(active)Color.WHITE else green).apply {
        setPadding(dp(14),dp(9),dp(14),dp(9))
        background=rounded(if(active)green else surface,20)
        setOnClickListener { click() }
    }
    private fun activityChips(parent:LinearLayout,refresh:()->Unit) {
        val wrap=row()
        listOf("All","Bills","Payments","Advances","Revenue").forEach { name ->
            wrap.addView(chip(name,activityFilter==name) { activityFilter=name;refresh() })
            wrap.addView(View(activity),LinearLayout.LayoutParams(dp(7),1))
        }
        parent.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false
            addView(wrap)
        });parent.addView(space(14))
    }
    private fun typeVisible(type:String):Boolean=when(activityFilter) {
        "Bills"->type=="BILL"
        "Payments"->type=="PAYMENT" || type=="ADJUSTMENT" || type=="REVERSAL"
        "Advances"->type=="ADVANCE" || type=="ADJUSTMENT" || type=="REVERSAL"
        "Revenue"->type=="REVENUE"
        else->true
    }
    private fun eventColor(type:String)=when(type) {
        "REVENUE"->green
        "PAYMENT","ADJUSTMENT"->green
        "ADVANCE","REVERSAL"->amber
        else->ink
    }
    private fun formatEventAmount(event:FinanceActivityStore.Event):String = when(event.type) {
        "PAYMENT","ADVANCE","ADJUSTMENT"->"−"+money(event.amount)
        "REVENUE"->"+"+money(event.amount)
        "REVERSAL"->"+"+money(event.amount)
        else->money(event.amount)
    }
    private fun eventRow(parent:LinearLayout,event:FinanceActivityStore.Event,
                         bills:Map<String,FinanceActivityStore.Bill>,openManual:(String)->Unit,
                         store:FinanceActivityStore) {
        val layout=col().apply { setPadding(0,dp(11),0,dp(11)) }
        val head=row().apply { gravity=Gravity.TOP }
        head.addView(col().apply {
            addView(txt(LocalDate.parse(event.date).format(dayFormat),11f,true,muted))
            addView(txt(LocalDate.parse(event.date).year.toString(),10f,false,muted))
        },LinearLayout.LayoutParams(dp(60),-2))
        head.addView(col().apply {
            addView(txt(event.title,14f,true).apply { maxLines=2 })
            addView(space(3))
            addView(txt(event.detail,11f,false,muted))
            if(event.type=="BILL") {
                bills[event.billId]?.let { bill ->
                    addView(space(4))
                    addView(txt("${bill.source.lowercase().replaceFirstChar { it.uppercase() }} · ${bill.status}",
                        11f,true,if(bill.due>0)amber else green))
                }
            }
            if(event.type=="ADJUSTMENT") {
                addView(space(2));addView(txt("Advance used · no new money paid",10f,false,amber))
            }
        },LinearLayout.LayoutParams(0,-2,1f))
        head.addView(col().apply {
            gravity=Gravity.RIGHT
            val salaryBill = event.billId?.let(bills::get)?.takeIf {
                event.type=="BILL" && it.source=="PAYROLL" && it.title.startsWith("Salary ·")
            }
            addView(txt(salaryBill?.let { money(it.due) } ?: formatEventAmount(event),14f,true,
                if(salaryBill!=null) (if(salaryBill.due>0)red else green) else eventColor(event.type)).apply {
                gravity=Gravity.END;maxLines=2
            })
            if(event.type=="BILL") bills[event.billId]?.let { bill ->
                addView(space(3))
                if(salaryBill!=null) {
                    val applied=store.salarySettlement(bill.id)
                    addView(txt("Gross expense ${money(bill.amount)}",10f,false,muted).apply { gravity=Gravity.END })
                    addView(txt("Advance used ${money(applied.advancePaise)}",10f,false,muted).apply { gravity=Gravity.END })
                    addView(txt("Salary paid ${money(applied.paidLaterPaise)}",10f,false,green).apply { gravity=Gravity.END })
                } else {
                    addView(txt("Paid ${money(bill.paid)}",10f,false,green).apply { gravity=Gravity.END })
                }
                addView(txt("Due ${money(bill.due)}",10f,true,if(bill.due>0)red else green).apply { gravity=Gravity.END })
            }
        },LinearLayout.LayoutParams(dp(119),-2))
        layout.addView(head)
        layout.setOnClickListener {
            val bill=event.billId?.let(bills::get)
            when {
                event.type=="PAYMENT" || event.type=="ADVANCE" -> showPaymentActions(event,store)
                bill!=null -> showBillActions(bill,store,openManual)
                event.vendorId.isNotBlank() -> showVendorLedger(event.vendorId)
            }
        }
        parent.addView(layout);parent.addView(line())
    }
    private fun showBillActions(b:FinanceActivityStore.Bill,store:FinanceActivityStore,onManual:(String)->Unit) {
        val choices=buildList {
            add("Bill details and dated payments")
            if(b.source=="PAYROLL") add("Open Manpower")
            else if(b.due>0 && !LocalDate.parse(b.dueDate).isAfter(LocalDate.now()))add("Record payment")
            if(!b.vendorId.startsWith("employee:") && b.vendorId!="no_payee") add("Vendor ledger")
            if(b.source=="MANUAL")add("Edit / delete draft (reason required)")
        }
        AlertDialog.Builder(activity).setTitle(b.title)
            .setMessage("Bill ${money(b.amount)}\nPaid ${money(b.paid)}\nDue ${money(b.due)}\n${b.status}")
            .setItems(choices.toTypedArray()) { _,index ->
                when(choices[index]) {
                    "Bill details and dated payments"->showBillHistory(b.id)
                    "Record payment"->showPaymentDialog(b.id)
                    "Open Manpower"->FinanceSalaryPlannerScreen(activity,onChanged).show()
                    "Vendor ledger"->showVendorLedger(b.vendorId)
                    else->onManual(b.id)
                }
            }.setNegativeButton("Close",null).show()
    }
    private fun showPaymentActions(event:FinanceActivityStore.Event,store:FinanceActivityStore) {
        val paymentId=store.paymentIdForEvent(event.id)
        if(paymentId==null) { toast("Payment reference unavailable");return }
        val employee=event.vendorId.startsWith("employee:")
        if(employee) {
            AlertDialog.Builder(activity).setTitle("Employee payment")
                .setMessage("Manage employee salary payments and allocations in Manpower.")
                .setPositiveButton("Open Manpower") { _,_->FinanceSalaryPlannerScreen(activity,onChanged).show() }
                .setNegativeButton("Close",null).show()
            return
        }
        val actions=arrayOf("Open Vendor Ledger","Reverse incorrect payment entry")
        AlertDialog.Builder(activity).setTitle(event.title).setItems(actions) { _,which ->
            when(which) {
                0 -> showVendorLedger(event.vendorId)
                1 -> {
                    val form=col().apply { setPadding(dp(18),dp(10),dp(18),dp(10)) }
                    form.addView(txt("Reversal retains the original transaction and reason. Only use for an erroneous local entry. This does not refund bank money.",12f,false,amber))
                    form.addView(space(10))
                    val reason=edit("","Why was this recorded incorrectly?")
                    field(form,"MANDATORY REVERSAL REASON",reason)
                    val alert=AlertDialog.Builder(activity).setTitle("Reverse recorded payment?")
                        .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Reverse",null).create()
                    alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            FinanceActivityStore(activity).use { it.reversePayment(paymentId,reason.text.toString()) }
                            alert.dismiss();onChanged()
                        } catch(e:Exception){ toast(e.message?:"Cannot reverse this payment") }
                    } }
                    alert.show()
                }
            }
        }.show()
    }
    private fun showBillHistory(billId:String) {
        FinanceActivityStore(activity).use { store ->
            val bill=store.bill(billId) ?: return
            val lines=store.events().filter { it.billId==billId && it.type!="BILL" }.sortedBy { it.date }
            val detail=buildString {
                append("Original bill: ${money(bill.amount)}\nPaid/settled: ${money(bill.paid)}\nRemaining: ${money(bill.due)}\n")
                if(bill.openingPaid>0)append("\nEarlier recorded paid: ${money(bill.openingPaid)} (actual payment date not stored in V16)\n")
                append("\nDated payment and allocation events:\n")
                if(lines.isEmpty())append("No dated payments in the Finance ledger.")
                else lines.forEach { append("${it.date} · ${it.type} · ${money(it.amount)}\n${it.detail}\n\n") }
            }
            AlertDialog.Builder(activity).setTitle(bill.title).setMessage(detail)
                .setPositiveButton("Close",null).show()
        }
    }
    /**
     * V25 dashboard activity rows delegate to the ORIGINAL bill, payment, reversal,
     * vendor-ledger and Manpower action handlers. No parallel transaction flow.
     */
    fun openExpenseEvent(
        eventId: String,
        drafts: List<FinanceDemoDraftBill>,
        onManualActions: (FinanceDemoDraftBill) -> Unit
    ) {
        try {
            loaded(drafts).use { store ->
                val event = store.events().firstOrNull { it.id == eventId }
                    ?: error("Activity entry no longer exists. Refresh Expenses.")
                val bill = event.billId?.let(store::bill)
                when {
                    event.type == "PAYMENT" || event.type == "ADVANCE" ->
                        showPaymentActions(event, store)
                    bill != null -> showBillActions(bill, store) { id ->
                        drafts.firstOrNull { "manual:${it.draftId}" == id }?.let(onManualActions)
                    }
                    event.vendorId.isNotBlank() && event.vendorId != "no_payee" ->
                        showVendorLedger(event.vendorId)
                    else -> AlertDialog.Builder(activity).setTitle(event.title)
                        .setMessage("${event.date}\n${event.detail}\n${money(event.amount)}")
                        .setPositiveButton("Close", null).show()
                }
            }
        } catch (error: Exception) {
            toast(error.message ?: "Could not open this activity")
        }
    }

    fun renderExpenses(parent:LinearLayout,drafts:List<FinanceDemoDraftBill>,filter:String,
                       onManualActions:(FinanceDemoDraftBill)->Unit,onPendingStock:()->Unit) {
        parent.removeAllViews()
        header(parent,"Finance Statement","Bills · Payments · Adjustments · Revenue")
        val selection = FinancePeriodSelectionV19(activity).read()
        FinancePeriodSelectionV19(activity).render(parent) {
            renderExpenses(parent,drafts,filter,onManualActions,onPendingStock)
        }
        try {
            loaded(drafts).use { store ->
                val today=LocalDate.now()
                // Monthly figures refer to the bill's accounting period, not a filtered payment date.
                val selectedBills = store.bills().filter { b ->
                    !LocalDate.parse(b.date).isAfter(today) &&
                        (selection.allTime || b.period == selection.month.toString())
                }
                fun exact(values:List<Long>):Long = values.fold(0L) { a,b -> Math.addExact(a,b) }
                val billed = exact(selectedBills.map { it.amount })
                val settled = exact(selectedBills.map { it.paid })
                val unpaid = exact(selectedBills.map { it.due })
                val selectedRevenue = if (selection.allTime) store.revenueRecordsAll()
                    else store.revenueRecords(selection.month.toString())
                        .filter { !LocalDate.parse(it.date).isAfter(today) }
                val overview = FinanceActivityStore.Overview(billed,settled,unpaid,0L,
                    exact(selectedRevenue.map { it.amountPaise }))
                val stock = selectedBills.count { it.stockPending }
                val taxDue:Long? = try {
                    val rents = FinanceFixedExpenseStore(activity).use { fixed ->
                        fixed.issueThrough(YearMonth.now()); fixed.bills()
                    }
                    FinanceLeaseTaxStore(activity).use { tax ->
                        tax.issueForBills(rents)
                        exact(tax.bills().filter { selection.allTime || it.month == selection.month.toString() }
                            .map { it.remainingPaise })
                    }
                } catch(error:Exception) {
                    parent.addView(txt("Rent tax balances need review: ${error.message}",12f,false,red))
                    null
                }
                summary(parent,overview,taxDue,stock,onPendingStock)
                val original=store.bills().associateBy { it.id }
                val draftsById=drafts.associateBy { "manual:${it.draftId}" }
                activityChips(parent) { renderExpenses(parent,drafts,filter,onManualActions,onPendingStock) }
                // Do not post unapproved payroll drafts as expense. Show actionable overdue reminder.
                if(activityFilter=="All" || activityFilter=="Bills") {
                    val pending=FinancePayrollStore(activity).use { payroll ->
                        payroll.runs().filter { it.status=="DRAFT" && !LocalDate.parse(it.dueDate).isAfter(today) }
                            .map { (payroll.employee(it.employeeId)?.name ?: "Employee") to it }
                    }
                    if(pending.isNotEmpty()) {
                        val total=pending.sumOf { it.second.amountPaise }
                        parent.addView(col().apply {
                            setPadding(dp(12),dp(11),dp(12),dp(11));background=rounded(surface,12)
                            addView(txt("Payroll due for approval · ${money(total)}",13f,true,red))
                            addView(txt(pending.joinToString("  ·  ") { "${it.first} ${it.second.period}" },11f,false,muted))
                            addView(txt("Not booked in bills until issued. Open Manpower  ›",11f,true,green))
                            setOnClickListener { FinanceSalaryPlannerScreen(activity,onChanged).show() }
                        })
                        parent.addView(space(10))
                    }
                }
                parent.addView(space(6))
                val shown=store.events().filter { !LocalDate.parse(it.date).isAfter(today) }
                    .filter { typeVisible(it.type) }.filter { e ->
                        val b=e.billId?.let(original::get)
                        (selection.allTime || (b?.period ?: e.date.take(7)) == selection.month.toString()) &&
                            (b==null || filter=="All" || b.status==filter)
                    }
                if(shown.isEmpty())parent.addView(txt("No transactions in this view.",13f,false,muted))
                var group=""
                shown.forEach { event ->
                    val month=YearMonth.from(LocalDate.parse(event.date)).toString()
                    if(month!=group) {
                        group=month;parent.addView(space(10))
                        parent.addView(txt(YearMonth.parse(month).format(monthFormat),12f,true,muted))
                        parent.addView(space(4))
                    }
                    eventRow(parent,event,original,{ id -> draftsById[id]?.let(onManualActions) },store)
                }
                if(selectedRevenue.isEmpty()) {
                    parent.addView(space(13))
                    parent.addView(txt("No locally imported revenue for this period. Live Booking Register revenue sync is not connected.",11f,false,muted))
                }
                if(original.values.any { it.openingPaid>0 }) {
                    parent.addView(space(9))
                    parent.addView(txt("Earlier manual drafts contain paid totals without payment dates. These are counted in balances, not shown as fabricated dated transactions.",11f,false,muted))
                }
            }
        } catch(error:Exception) {
            parent.addView(txt("Ledger review needed: ${error.message ?: "Unable to read data"}",13f,true,red))
        }
        parent.addView(space(88))
    }
    fun renderPayments(parent:LinearLayout,drafts:List<FinanceDemoDraftBill>,
                       onManualPreview:(FinanceDemoDraftBill)->Unit) {
        parent.removeAllViews()
        header(parent,"Payments & Dues","One bill · one balance · pay any outstanding amount")
        val selection = FinancePeriodSelectionV19(activity).read()
        FinancePeriodSelectionV19(activity).render(parent) { renderPayments(parent,drafts,onManualPreview) }
        try {
            loaded(drafts).use { store ->
                val today=LocalDate.now()
                val due=store.outstanding(today).filter { selection.allTime || it.period == selection.month.toString() }
                val dueNow=due.fold(0L) { amount,bill -> Math.addExact(amount,bill.due) }
                parent.addView(col().apply {
                    setPadding(dp(13),dp(13),dp(13),dp(13));background=rounded(surface,13)
                    addView(txt("Bills due now · ${selection.periodLabel}",12f,false,muted))
                    addView(space(4))
                    addView(txt(money(dueNow),26f,true,if(dueNow>0)red else green))
                    addView(txt("Not-yet-due bills remain in Statement unpaid totals.",11f,false,muted))
                })
                parent.addView(space(12))
                parent.addView(txt("Bills to be paid",17f,true))
                parent.addView(space(7))
                if(due.isEmpty())parent.addView(txt("No bills are outstanding.",13f,false,muted))
                due.forEach { bill ->
                    val item=col().apply { setPadding(dp(12),dp(12),dp(12),dp(12));background=rounded(surface,12) }
                    item.addView(row().apply {
                        addView(col().apply {
                            addView(txt(bill.title,14f,true))
                            addView(txt("${bill.vendor} · ${bill.period}",11f,false,muted))
                            addView(txt("${bill.source} · Due ${bill.dueDate}",10f,false,muted))
                        },LinearLayout.LayoutParams(0,-2,1f))
                        addView(col().apply {
                            gravity=Gravity.RIGHT
                            addView(txt(money(bill.due),17f,true,red).apply { gravity=Gravity.END })
                            addView(txt(bill.status,11f,true,amber).apply { gravity=Gravity.END })
                        })
                    })
                    item.addView(space(8))
                    item.addView(row().apply {
                        addView(txt("Bill ${money(bill.amount)} · Paid ${money(bill.paid)}",11f,false,muted),
                            LinearLayout.LayoutParams(0,-2,1f))
                        addView(chip("Pay  ›",false) {
                            showPaymentDialog(bill.id)
                        })
                    })
                    parent.addView(item);parent.addView(space(9))
                }
                parent.addView(space(11))
                parent.addView(txt("Finance DEV: recording a payment documents a local claim; it does not transfer money or post a journal.",11f,false,muted))
            }
        } catch(error:Exception) {
            parent.addView(txt("Unable to calculate dues: ${error.message ?: "Unknown error"}",13f,true,red))
        }
        parent.addView(space(88))
    }
    private fun showPaymentDialog(billId:String) {
        try { FinanceSalaryDueService.reconcileIssuedAdvances(activity) } catch(error:Exception) {
            toast("Salary advance review needed: ${error.message}");return
        }
        FinanceActivityStore(activity).use { store ->
            val bill=store.bill(billId) ?: run { toast("Bill not found");return }
            // Payroll can be paid directly here; advances are reconciled before bill lookup.
            if(bill.due<=0) { toast("Bill already settled");return }
            val form=col().apply { setPadding(dp(18),dp(7),dp(18),dp(6)) }
            val amount=edit(money(bill.due).removePrefix("₹").replace(",",""),"Amount ₹",true)
            val date=edit(LocalDate.now().toString(),"YYYY-MM-DD")
            val methods=listOf("Bank","UPI","Cash","Other")
            val method=spinner(methods)
            val reference=edit("","Transaction reference (recommended)")
            form.addView(txt("Bill ${money(bill.amount)} · Settled ${money(bill.paid)} · Due ${money(bill.due)}",12f,true))
            form.addView(space(12))
            field(form,"PAYMENT AMOUNT",amount);field(form,"ACTUAL PAYMENT DATE",date)
            field(form,"METHOD",method);field(form,"REFERENCE",reference)
            val alert=AlertDialog.Builder(activity).setTitle("Pay · ${bill.title}")
                .setView(ScrollView(activity).apply { addView(form) })
                .setNegativeButton("Cancel",null).setPositiveButton("Record Payment",null).create()
            alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val paise=FinanceFixedPolicy.amount(amount.text.toString())
                    FinanceActivityStore(activity).use { ledger ->
                        ledger.payBill(billId,paise,date.text.toString(),methods[method.selectedItemPosition],
                            reference.text.toString())
                    }
                    alert.dismiss();onChanged()
                } catch(error:Exception) { toast(error.message ?: "Could not record payment") }
            }}
            alert.show()
        }
    }
    private fun showAdvanceDialog(vendorId:String? = null) {
        val vendorChoices=HotelPurchaseVendorStore(activity).all().filter { vendorId == null || it.id == vendorId }
        if(vendorChoices.isEmpty()) { toast("Add a vendor in Settings > Masters > Vendor Master first");return }
        val form=col().apply { setPadding(dp(18),dp(9),dp(18),dp(6)) }
        val vendor=spinner(vendorChoices.map{it.name}).apply { isEnabled = vendorId == null }
        val amount=edit("","Amount ₹",true)
        val date=edit(LocalDate.now().toString(),"YYYY-MM-DD")
        val methods=listOf("Bank","UPI","Cash","Other")
        val method=spinner(methods)
        val reference=edit("","Transaction reference (recommended)")
        field(form,"VENDOR",vendor);field(form,"ADVANCE AMOUNT",amount);field(form,"PAYMENT DATE",date)
        field(form,"METHOD",method);field(form,"REFERENCE",reference)
        val alert=AlertDialog.Builder(activity).setTitle("Vendor advance")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel",null).setPositiveButton("Record Advance",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val v=vendorChoices[vendor.selectedItemPosition]
                FinanceActivityStore(activity).use { it.advance(v.id,v.name,FinanceFixedPolicy.amount(amount.text.toString()),
                    date.text.toString(),methods[method.selectedItemPosition],reference.text.toString()) }
                alert.dismiss();onChanged()
            } catch(error:Exception){ toast(error.message ?: "Advance not recorded") }
        }}
        alert.show()
    }
    private fun showAllocateAdvance(advanceId:String) {
        FinanceActivityStore(activity).use { store ->
            val advance=store.payments().firstOrNull { it.id==advanceId && it.kind=="ADVANCE" && it.available>0 }
                ?: run { toast("Advance already settled");return }
            val bills=store.outstanding().filter { it.vendorId==advance.vendorId }
            if(bills.isEmpty()) { toast("This vendor has no unpaid bill to allocate against");return }
            val labels=bills.map { "${it.title} · ${it.period} · Due ${money(it.due)}" }
            AlertDialog.Builder(activity).setTitle("Apply ${money(advance.available)} advance")
                .setItems(labels.toTypedArray()) { _,index ->
                    val bill=bills[index]
                    val form=col().apply { setPadding(dp(18),dp(12),dp(18),dp(8)) }
                    val amount=edit(money(minOf(advance.available,bill.due)).removePrefix("₹").replace(",",""),"Amount ₹",true)
                    field(form,"AMOUNT TO ADJUST",amount)
                    form.addView(txt("This uses an existing advance; no new cash payment will be recorded.",12f,false,muted))
                    val alert=AlertDialog.Builder(activity).setTitle("Allocate to ${bill.title}")
                        .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Allocate",null).create()
                    alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            FinanceActivityStore(activity).use {
                                it.allocateAdvance(advanceId,bill.id,FinanceFixedPolicy.amount(amount.text.toString()),LocalDate.now().toString())
                            }
                            alert.dismiss();onChanged()
                        } catch(error:Exception){ toast(error.message ?: "Allocation not saved") }
                    }}
                    alert.show()
                }.setNegativeButton("Cancel",null).show()
        }
    }
    private fun showVendorChooser() {
        FinanceActivityStore(activity).use { store ->
            val vendors=(store.bills().map { it.vendorId to it.vendor } +
                store.payments().map { it.vendorId to it.vendor }).distinctBy { it.first }
                .sortedBy { it.second }
            if(vendors.isEmpty()) { toast("No vendor records yet");return }
            AlertDialog.Builder(activity).setTitle("Vendor ledgers")
                .setItems(vendors.map { it.second }.toTypedArray()) { _,index -> showVendorLedger(vendors[index].first) }
                .setNegativeButton("Close",null).show()
        }
    }
    // Finance V25: legacy entry points now route to the unified Vendors tab.
    fun openLedgerHub() {
        val host = activity as? FinanceDevSafetyActivity
        if (host != null) host.openV25Vendor(null)
        else toast("Open Vendors from the Finance navigation")
    }
    /** V25 profile actions use the ORIGINAL vendor advance and allocation dialogs. */
    fun recordVendorAdvance(vendorId:String) {
        if(vendorId.isBlank() || vendorId=="no_payee" || vendorId.startsWith("employee:")) {
            toast("Only a vendor can receive a vendor advance");return
        }
        showAdvanceDialog(vendorId)
    }
    fun allocateVendorAdvance(paymentId:String) {
        if(paymentId.isBlank()) { toast("Advance reference missing");return }
        showAllocateAdvance(paymentId)
    }
    /** Pay a supplier only through the established V17 payable ledger method. */
    fun payVendorBills(vendorId:String) {
        if(vendorId.isBlank() || vendorId == "no_payee" || vendorId.startsWith("employee:")) {
            toast("Only suppliers can be paid here. Staff payments belong in Manpower.");return
        }
        try {
            FinanceActivityStore(activity).use { store ->
                val open = store.bills().filter { it.vendorId == vendorId && it.due > 0L }
                if(open.isEmpty()) { toast("No outstanding vendor bills");return }
                AlertDialog.Builder(activity).setTitle("Select bill to pay")
                    .setItems(open.map { "${it.title} · ${it.period} · ${money(it.due)} due" }.toTypedArray()) { _, index ->
                        showPaymentDialog(open[index].id)
                    }.setNegativeButton("Cancel",null).show()
            }
        } catch(error:Exception) { toast(error.message ?: "Unable to open vendor payments") }
    }
    fun openPayeeLedger(vendorId:String) { showVendorLedger(vendorId) }
    // Direct individual routing: no second vendor list or legacy ledger page.
    private fun showVendorLedger(vendorId:String) {
        if(vendorId.startsWith("employee:")) {
            FinanceSalaryPlannerScreen(activity,onChanged).showForEmployee(vendorId.removePrefix("employee:"))
            return
        }
        if(vendorId.isBlank() || vendorId=="no_payee") {
            toast("No vendor associated with this entry")
            return
        }
        val host = activity as? FinanceDevSafetyActivity
        if(host != null) host.openV25Vendor(vendorId)
        else toast("Open Vendors from the Finance navigation")
    }
}

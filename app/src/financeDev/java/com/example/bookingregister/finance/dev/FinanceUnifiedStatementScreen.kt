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
    private fun header(parent:LinearLayout,title:String,detail:String) {
        parent.addView(row().apply {
            addView(col().apply {
                addView(txt(title,23f,true));addView(space(3));addView(txt(detail,12f,false,muted))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(txt("Recurring  ›",13f,true,green).apply {
                setPadding(dp(6),dp(10),0,dp(10))
                setOnClickListener { FinanceFixedExpensesScreen(activity,onChanged).show() }
            })
        });parent.addView(space(14))
    }
    private fun summary(parent:LinearLayout,total:FinanceActivityStore.Overview) {
        val content=row().apply { background=rounded(surface,15);setPadding(dp(10),dp(13),dp(10),dp(13)) }
        listOf(Triple("Bills",total.billed,ink),Triple("Paid / settled",total.settled,green),
            Triple("Unpaid",total.due,red)).forEachIndexed { index,part ->
            val cell=col().apply {
                setPadding(dp(6),0,dp(3),0)
                addView(txt(part.first,10.5f,false,muted));addView(space(5))
                addView(txt(money(part.second),14.5f,true,part.third).apply { maxLines=2 })
            }
            if(index>0) content.addView(View(activity).apply { setBackgroundColor(rule) },LinearLayout.LayoutParams(dp(1),dp(42)))
            content.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
        }
        parent.addView(content)
        parent.addView(space(9))
        if(total.advances>0L)parent.addView(txt("Unallocated vendor advances: ${money(total.advances)}",12f,true,amber))
        if(total.revenue!=0L)parent.addView(txt("Imported revenue: ${money(total.revenue)}",12f,true,green))
        parent.addView(space(14))
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
        "Payments"->type=="PAYMENT" || type=="ADJUSTMENT"
        "Advances"->type=="ADVANCE" || type=="ADJUSTMENT"
        "Revenue"->type=="REVENUE"
        else->true
    }
    private fun eventColor(type:String)=when(type) {
        "REVENUE"->green
        "PAYMENT","ADJUSTMENT"->green
        "ADVANCE"->amber
        else->ink
    }
    private fun formatEventAmount(event:FinanceActivityStore.Event):String = when(event.type) {
        "PAYMENT","ADVANCE","ADJUSTMENT"->"−"+money(event.amount)
        "REVENUE"->"+"+money(event.amount)
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
            addView(txt(formatEventAmount(event),14f,true,eventColor(event.type)).apply {
                gravity=Gravity.END;maxLines=2
            })
            if(event.type=="BILL") bills[event.billId]?.let { bill ->
                addView(space(3))
                addView(txt("Paid ${money(bill.paid)}",10f,false,green).apply { gravity=Gravity.END })
                addView(txt("Due ${money(bill.due)}",10f,true,if(bill.due>0)red else green).apply { gravity=Gravity.END })
            }
        },LinearLayout.LayoutParams(dp(119),-2))
        layout.addView(head)
        layout.setOnClickListener {
            val bill=event.billId?.let(bills::get)
            when {
                bill!=null -> showBillActions(bill,store,openManual)
                event.vendorId.isNotBlank() -> showVendorLedger(event.vendorId)
            }
        }
        parent.addView(layout);parent.addView(line())
    }
    private fun showBillActions(b:FinanceActivityStore.Bill,store:FinanceActivityStore,onManual:(String)->Unit) {
        val choices=buildList {
            add("Bill details and dated payments")
            if(b.due>0 && !LocalDate.parse(b.dueDate).isAfter(LocalDate.now()))add("Record payment")
            add("Vendor ledger")
            if(b.source=="MANUAL")add("Edit / stock / history")
        }
        AlertDialog.Builder(activity).setTitle(b.title)
            .setMessage("Bill ${money(b.amount)}\nPaid ${money(b.paid)}\nDue ${money(b.due)}\n${b.status}")
            .setItems(choices.toTypedArray()) { _,index ->
                when(choices[index]) {
                    "Bill details and dated payments"->showBillHistory(b.id)
                    "Record payment"->showPaymentDialog(b.id)
                    "Vendor ledger"->showVendorLedger(b.vendorId)
                    else->onManual(b.id)
                }
            }.setNegativeButton("Close",null).show()
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
    fun renderExpenses(parent:LinearLayout,drafts:List<FinanceDemoDraftBill>,filter:String,
                       onManualActions:(FinanceDemoDraftBill)->Unit,onPendingStock:()->Unit) {
        parent.removeAllViews()
        header(parent,"Finance Statement","Bills · Payments · Adjustments · Revenue")
        try {
            loaded(drafts).use { store ->
                val today=LocalDate.now()
                val overview=store.overview(today)
                summary(parent,overview)
                val stock=store.bills().count { it.stockPending }
                if(stock>0) {
                    parent.addView(txt("$stock purchase(s) need item quantities  ›",12f,true,amber).apply {
                        setPadding(dp(2),dp(6),0,dp(13));setOnClickListener { onPendingStock() }
                    })
                }
                val original=store.bills().associateBy { it.id }
                val draftsById=drafts.associateBy { "manual:${it.draftId}" }
                activityChips(parent) { renderExpenses(parent,drafts,filter,onManualActions,onPendingStock) }
                parent.addView(row().apply {
                    addView(txt("Transactions",17f,true),LinearLayout.LayoutParams(0,-2,1f))
                    addView(txt(if(filter=="All")"All bills" else filter,11f,false,muted))
                });parent.addView(space(9))
                val shown=store.events().filter { !LocalDate.parse(it.date).isAfter(today) }
                    .filter { typeVisible(it.type) }.filter { e ->
                        val b=e.billId?.let(original::get)
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
                if(overview.revenue==0L) {
                    parent.addView(space(13))
                    parent.addView(txt("Booking Register revenue sync is not connected yet. No revenue figures have been invented.",11f,false,muted))
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
        try {
            loaded(drafts).use { store ->
                val today=LocalDate.now()
                val overview=store.overview(today)
                parent.addView(col().apply {
                    setPadding(dp(13),dp(13),dp(13),dp(13));background=rounded(surface,13)
                    addView(txt("Total unpaid bills",12f,false,muted))
                    addView(space(4));addView(txt(money(overview.due),26f,true,if(overview.due>0)red else green))
                    addView(txt("Vendor advances available ${money(overview.advances)}",12f,false,muted))
                })
                parent.addView(space(12))
                val actions=row()
                actions.addView(chip("Vendor ledgers  ›",false) { showVendorChooser() },LinearLayout.LayoutParams(0,-2,1f))
                actions.addView(View(activity),LinearLayout.LayoutParams(dp(7),1))
                actions.addView(chip("+ Vendor advance",false) { showAdvanceDialog() },LinearLayout.LayoutParams(0,-2,1f))
                parent.addView(actions);parent.addView(space(16))
                val due=store.outstanding(today)
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
                        addView(chip("Pay  ›",false) { showPaymentDialog(bill.id) })
                    })
                    parent.addView(item);parent.addView(space(9))
                }
                if(overview.advances>0L) {
                    parent.addView(space(9));parent.addView(txt("Unused vendor advances",17f,true));parent.addView(space(7))
                    store.payments().filter { it.kind=="ADVANCE" && it.available>0 }.forEach { advance ->
                        val item=row().apply { setPadding(dp(10),dp(11),dp(10),dp(11)) }
                        item.addView(col().apply {
                            addView(txt(advance.vendor,14f,true));addView(txt("${advance.date} · Available ${money(advance.available)}",11f,false,muted))
                        },LinearLayout.LayoutParams(0,-2,1f))
                        item.addView(chip("Allocate  ›",false) { showAllocateAdvance(advance.id) })
                        parent.addView(item);parent.addView(line())
                    }
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
        FinanceActivityStore(activity).use { store ->
            val bill=store.bill(billId) ?: run { toast("Bill not found");return }
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
    private fun showAdvanceDialog() {
        val vendorChoices=HotelPurchaseVendorStore(activity).all()
        if(vendorChoices.isEmpty()) { toast("Add a vendor in Purchase > Vendors first");return }
        val form=col().apply { setPadding(dp(18),dp(9),dp(18),dp(6)) }
        val vendor=spinner(vendorChoices.map { it.name })
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
    private fun showVendorLedger(vendorId:String) {
        FinanceActivityStore(activity).use { store ->
            val bills=store.bills().filter { it.vendorId==vendorId }
            val payments=store.payments().filter { it.vendorId==vendorId }
            val events=store.events().filter { it.vendorId==vendorId }
                .filter { !LocalDate.parse(it.date).isAfter(LocalDate.now()) }
                .sortedWith(compareBy<FinanceActivityStore.Event> { it.date }
                    .thenBy { if(it.type=="BILL")0 else if(it.type=="ADVANCE")1 else 2 }.thenBy { it.id })
            val due=bills.filter { !LocalDate.parse(it.date).isAfter(LocalDate.now()) }.sumOf { it.due }
            val credit=payments.filter { it.kind=="ADVANCE" }.sumOf { it.available }
            val name=bills.firstOrNull()?.vendor ?: payments.firstOrNull()?.vendor ?: "Vendor"
            val container=col().apply { setPadding(dp(16),dp(10),dp(16),dp(12)) }
            container.addView(txt("Outstanding bills ${money(due)}",17f,true,if(due>0)red else green))
            container.addView(txt("Unallocated advances ${money(credit)}",13f,false,amber))
            container.addView(space(14))
            if(events.isEmpty())container.addView(txt("No dated transactions.",13f,false,muted))
            var running=0L
            events.forEach { event ->
                if(event.type=="BILL") running=Math.addExact(running,event.amount)
                if(event.type=="PAYMENT" || event.type=="ADJUSTMENT") running=Math.subtractExact(running,event.amount)
                val row=col().apply { setPadding(0,dp(7),0,dp(7)) }
                row.addView(txt("${event.date} · ${event.title}",12f,true))
                row.addView(txt("${formatEventAmount(event)} · ${event.detail}",11f,false,eventColor(event.type)))
                row.addView(txt("Payables after dated events: ${money(running)}",10f,false,muted))
                container.addView(row);container.addView(line())
            }
            if(bills.any { it.openingPaid>0 }) {
                container.addView(space(10))
                container.addView(txt("Earlier manual payment totals lack dates; they are included in dues but not assigned a fictitious transaction date. Running balances above omit these undated opening settlements.",11f,false,amber))
            }
            AlertDialog.Builder(activity).setTitle("Vendor Ledger · $name")
                .setView(ScrollView(activity).apply { addView(container) })
                .setPositiveButton("Close",null).show()
        }
    }
}

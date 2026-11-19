package com.example.bookingregister.finance.dev

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.YearMonth

/** Offline finance DEV management UI: scheduled bills, revisions, dues, payments and history. */
internal class FinanceFixedExpensesScreen(private val activity: AppCompatActivity, private val onClosed: () -> Unit = {}) {
    private val db by lazy { FinanceFixedExpenseStore(activity) }
    private var dialog: Dialog? = null
    private val navy = Color.rgb(24, 42, 65)
    private val muted = Color.rgb(103, 116, 132)
    private val green = Color.rgb(7, 111, 82)
    private val pale = Color.rgb(239, 246, 243)
    private val edge = Color.rgb(224, 231, 237)
    private val background = Color.rgb(247, 249, 252)
    private val danger = Color.rgb(186, 57, 63)
    private fun dp(x: Int) = (x * activity.resources.displayMetrics.density + .5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(h: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1,dp(h)) }
    private fun rounded(fill:Int,stroke:Int=edge,radius:Int=16) = GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(1),stroke)
    }
    private fun txt(value:String,size:Float=14f,bold:Boolean=false,color:Int=navy)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold) setTypeface(null,Typeface.BOLD)
    }
    private fun card() = column().apply { background=rounded(Color.WHITE);setPadding(dp(17),dp(15),dp(17),dp(15)) }
    private fun action(name:String,primary:Boolean=false,click:()->Unit)=TextView(activity).apply {
        text=name;textSize=14f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
        setTextColor(if(primary)Color.WHITE else green);minHeight=dp(45)
        setPadding(dp(11),dp(9),dp(11),dp(9));background=rounded(if(primary)green else pale,if(primary)green else pale,12)
        setOnClickListener { click() }
    }
    private fun edit(value:String,hint:String,number:Boolean=false)=EditText(activity).apply {
        setText(value);this.hint=hint;textSize=15f;setSingleLine(true)
        setPadding(dp(12),dp(10),dp(12),dp(10));background=rounded(Color.WHITE,edge,11)
        if(number) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun spinner(values:List<String>) = Spinner(activity).apply {
        adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,values)
        background=rounded(Color.WHITE,edge,10);minimumHeight=dp(45)
    }
    private fun field(form:LinearLayout,label:String,control:View) {
        form.addView(txt(label,12f,true,muted));form.addView(gap(5));form.addView(control,LinearLayout.LayoutParams(-1,dp(48)));form.addView(gap(12))
    }
    private fun notify(text:String) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show() }

    fun show() {
        dialog=Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar).also { it.setOnDismissListener { onClosed() }; it.show() }
        render()
    }
    /** Payments tab opens the ORIGINAL fixed-bill payment dialog, never a copy. */
    fun payBill(billId: String) {
        try {
            db.issueThrough(YearMonth.now())
            val bill = FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(includeManual=false)
                val accounted=ledger.bill(billId)
                db.bills().firstOrNull { it.id == billId }?.copy(paid=accounted?.paid ?: 0L)
            }
                ?: error("Fixed bill not found. Reopen Payments.")
            require(bill.remaining > 0L) { "This bill has already been paid." }
            require(!LocalDate.parse(bill.dueDate).isAfter(LocalDate.now())) {
                "This bill is not due yet. Use Recurring to review upcoming obligations."
            }
            showPayment(bill)
        } catch (error: Exception) {
            notify(error.message ?: "Unable to open fixed-bill payment")
        }
    }

    private fun render() {
        try { db.issueThrough(YearMonth.now()) }
        catch(e:Exception) { notify(e.message?:"Unable to load scheduled bills") }
        val d=dialog?:return
        val root=column().apply { setBackgroundColor(this@FinanceFixedExpensesScreen.background) }
        val header=row().apply { setPadding(dp(18),dp(20),dp(18),dp(14));setBackgroundColor(Color.WHITE) }
        header.addView(action("‹") { d.dismiss() },LinearLayout.LayoutParams(dp(48),dp(46)))
        header.addView(column().apply {
            addView(txt("Fixed Expenses",21f,true));addView(txt("Recurring commitments · Finance DEV",12f,false,muted))
        },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        val scroller=ScrollView(activity).apply { isFillViewport=false;clipToPadding=false }
        val content=column().apply { setPadding(dp(16),dp(18),dp(16),dp(28)) }
        val rules=db.rules()
        // V17 is the authoritative local balance for both manual and recurring bills.
        val ledgerBills=FinanceActivityStore(activity).use { ledger ->
            ledger.synchronize(includeManual=false)
            ledger.bills().associateBy { it.id }
        }
        val bills=db.bills().map { b -> b.copy(paid=ledgerBills[b.id]?.paid ?: b.paid) }
        val open=bills.filter { it.remaining>0L && !LocalDate.parse(it.dueDate).isAfter(LocalDate.now()) }
        val overdue=open.filter { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }
        val hero=card().apply { background=rounded(pale,pale,20)
            addView(txt("OUTSTANDING RECURRING DUES",11f,true,green))
            addView(gap(5));addView(txt(FinanceFixedPolicy.rupees(open.sumOf{it.remaining}),29f,true))
            addView(txt("${overdue.size} overdue bills  ·  ${rules.count{it.active}} active schedules",12f,false,muted))
        }
        content.addView(hero);content.addView(gap(14))
        content.addView(action("+  Add Fixed Expense",true) { showAdd() });content.addView(gap(12))
        content.addView(row().apply {
            addView(action("Refresh Bills") { render() },LinearLayout.LayoutParams(0,dp(45),1f))
            addView(View(activity),LinearLayout.LayoutParams(dp(8),1))
            addView(action("Audit History") { showHistory() },LinearLayout.LayoutParams(0,dp(45),1f))
        });content.addView(gap(18))
        content.addView(txt("Payment reminders",18f,true));content.addView(gap(4))
        content.addView(txt("Bills are snapshots. Payments reduce dues, never expense twice.",12f,false,muted));content.addView(gap(9))
        if(open.isEmpty())content.addView(card().apply {addView(txt("No payment is currently due.",14f,false,muted))})
        open.take(40).forEach { bill ->
            content.addView(card().apply {
                addView(row().apply {
                    addView(column().apply {
                        addView(txt(bill.type,15f,true));addView(txt(bill.vendor+"  ·  "+bill.month,12f,false,muted))
                    },LinearLayout.LayoutParams(0,-2,1f))
                    addView(txt(FinanceFixedPolicy.rupees(bill.remaining),16f,true,if(LocalDate.parse(bill.dueDate)<LocalDate.now())danger else navy))
                })
                addView(gap(9));addView(txt("Due ${bill.dueDate}  ·  ${bill.department}",12f,false,muted));addView(gap(9))
                addView(action("Record Payment") { showPayment(bill) })
            });content.addView(gap(9))
        }
        content.addView(gap(12));content.addView(txt("Recurring contracts",18f,true));content.addView(gap(9))
        if(rules.isEmpty())content.addView(card().apply{addView(txt("Create your first recurring obligation above.",14f,false,muted))})
        rules.forEach { rule ->
            val current=db.revisions(rule.id).lastOrNull()?.second ?:0L
            content.addView(card().apply {
                addView(row().apply {
                    addView(txt(rule.type,15f,true),LinearLayout.LayoutParams(0,-2,1f))
                    addView(txt(if(rule.active)"ACTIVE" else "STOPPED",11f,true,if(rule.active)green else muted))
                })
                addView(gap(6));addView(txt(rule.vendor+" · "+rule.department,12f,false,muted))
                addView(txt("Current rate ${FinanceFixedPolicy.rupees(current)}   ·   Due day ${rule.dueDay}",13f,true))
                addView(txt("From ${rule.startMonth}"+(rule.endMonth?.let { "  ·  Until $it" }?:""),12f,false,muted))
                if(rule.active){addView(gap(12));addView(row().apply {
                    addView(action("Change Amount") { showRevision(rule) },LinearLayout.LayoutParams(0,dp(44),1f))
                    addView(View(activity),LinearLayout.LayoutParams(dp(8),1))
                    addView(action("Stop") { showStop(rule) },LinearLayout.LayoutParams(0,dp(44),.65f))
                })}
            });content.addView(gap(9))
        }
        content.addView(gap(10));content.addView(txt("OFFLINE DEV · These are local schedules and payment records, not posted accounting journals.",11f,false,muted))
        scroller.addView(content);root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
    }
    private fun showAdd() {
        val catalog=FinanceExpenseCatalogLoader.load(activity)
        val visibility=FinanceExpenseVisibility(activity)
        val categories=catalog.categories.filter {
            it.code != FinanceExpenseCategoryRules.STAFF_ONLY && visibility.categoryVisible(it)
        }
        if(categories.isEmpty()) {notify("Enable a non-staff expense category in Add Expense first.");return}
        val vendors=HotelPurchaseVendorStore(activity).all()
        if(vendors.isEmpty()){notify("Create a vendor in Settings > Masters > Vendor Master first.");return}
        val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(8)) }
        val category=spinner(categories.map{it.name})
        val types=spinner(categories.first().types.map{it.name})
        category.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                types.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,categories[position].types.map{it.name})
            }
        }
        val vendor=spinner(vendors.map{it.name})
        vendor.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                val preferred=HotelPurchaseVendorStore(activity).typeCodes(vendors[position].id)
                val match=categories.flatMapIndexed { ci,c ->
                    c.types.mapIndexedNotNull { ti,t -> if(t.code in preferred)Triple(ci,ti,t.code) else null }
                }.firstOrNull()
                if(match!=null) {
                    category.setSelection(match.first)
                    types.post { types.setSelection(match.second) }
                }
            }
        }
        val departments=FinanceGlobalCatalogDefaults.departments
        val dept=spinner(departments)
        val amount=edit("","₹ Monthly base amount",true)
        val day=edit("5","1 to 31",true)
        val start=edit(YearMonth.now().toString(),"YYYY-MM")
        field(form,"CATEGORY",category);field(form,"EXPENSE TYPE",types)
        field(form,"VENDOR",vendor);field(form,"DEPARTMENT",dept)
        field(form,"MONTHLY AMOUNT (EXCLUDING SEPARATELY ACCOUNTED TAX)",amount)
        field(form,"DUE DAY OF MONTH",day);field(form,"START MONTH (YYYY-MM)",start)
        form.addView(txt("Recurring bills are generated by month. GST and statutory posting require the future accounting engine.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("New Fixed Expense").setView(ScrollView(activity).apply{addView(form)})
            .setNegativeButton("Cancel",null).setPositiveButton("Create",null).create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val c=categories[category.selectedItemPosition]
                    val t=c.types[types.selectedItemPosition]
                    val v=vendors[vendor.selectedItemPosition]
                    db.create(c.code,c.name,t.code,t.name,v.id,v.name,departments[dept.selectedItemPosition],
                        day.text.toString().toInt(),start.text.toString().trim(),FinanceFixedPolicy.amount(amount.text.toString()))
                    alert.dismiss();render()
                } catch(e:Exception){notify(e.message?:"Please check the contract details")}
            }
        };alert.show()
    }
    private fun showRevision(rule:FinanceFixedExpenseStore.Rule) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val amount=edit("","New monthly amount",true)
        val last=db.latestBillMonth(rule.id)
        val from=edit(maxOf(YearMonth.now().plusMonths(1),last?.plusMonths(1)?:YearMonth.now()).toString(),"YYYY-MM")
        val reason=edit("","Reason / contract reference")
        field(form,"NEW MONTHLY AMOUNT",amount);field(form,"EFFECTIVE FROM (YYYY-MM)",from);field(form,"MANDATORY REASON",reason)
        form.addView(txt("Already issued bills and payments retain their historical values.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("Revise ${rule.type}")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save Revision",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { db.revise(rule,FinanceFixedPolicy.amount(amount.text.toString()),from.text.toString().trim(),reason.text.toString())
                alert.dismiss();render() } catch(e:Exception){ notify(e.message?:"Unable to revise") }
        }};alert.show()
    }
    private fun showStop(rule:FinanceFixedExpenseStore.Rule) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val last=db.latestBillMonth(rule.id)
        val month=edit(maxOf(YearMonth.now().plusMonths(1),last?.plusMonths(1)?:YearMonth.now().plusMonths(1)).toString(),"YYYY-MM")
        val reason=edit("","Reason")
        field(form,"STOP GENERATING FROM (YYYY-MM)",month);field(form,"MANDATORY REASON",reason)
        val alert=AlertDialog.Builder(activity).setTitle("Stop recurring obligation?")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Stop Schedule",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { db.stop(rule,month.text.toString().trim(),reason.text.toString());alert.dismiss();render() }
            catch(e:Exception){notify(e.message?:"Unable to stop")}
        }};alert.show()
    }
    private fun showPayment(bill:FinanceFixedExpenseStore.Bill) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val amount=edit(FinanceFixedPolicy.rupees(bill.remaining).removePrefix("₹"),"Amount ₹",true)
        val date=edit(LocalDate.now().toString(),"YYYY-MM-DD")
        val methods=listOf("Bank","UPI","Cash","Other")
        val method=spinner(methods);val reference=edit("","Reference / remarks")
        field(form,"PAYMENT AMOUNT",amount);field(form,"ACTUAL PAYMENT DATE",date)
        field(form,"METHOD",method);field(form,"REFERENCE",reference)
        val alert=AlertDialog.Builder(activity).setTitle("Payment · ${bill.type}")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinanceActivityStore(activity).use { ledger ->
                    ledger.synchronize(includeManual=false)
                    ledger.payBill(bill.id,FinanceFixedPolicy.amount(amount.text.toString()),date.text.toString().trim(),methods[method.selectedItemPosition],reference.text.toString())
                }
                alert.dismiss();render();onClosed() }catch(e:Exception){notify(e.message?:"Unable to record payment")}
        }};alert.show()
    }
    private fun showHistory() {
        val text=db.history().joinToString("\n\n") { "${java.time.Instant.ofEpochMilli(it.at)}  ·  ${it.action}\n${it.detail}" }.ifBlank {"No events yet"}
        AlertDialog.Builder(activity).setTitle("Recurring expense audit history").setMessage(text)
            .setPositiveButton("Close",null).show()
    }
}

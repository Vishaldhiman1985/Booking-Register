package com.example.bookingregister.finance.dev

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.EditText
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
import java.util.Locale

/** Offline V18 Manpower: explicit attendance calendar, automatic month-end company credits. */
internal class FinanceSalaryPlannerScreen(
    private val activity:AppCompatActivity,
    private val onChanged:()->Unit,
    private val masterMode:Boolean = false
) {
    private var dialog:Dialog?=null
    private var period=YearMonth.now()
    private var selectedEmployeeId:String?=null
    private var selectedTab:Int=0
    private val ink=Color.rgb(24,42,65)
    private val green=Color.rgb(7,111,75)
    private val muted=Color.rgb(102,117,131)
    private val red=Color.rgb(178,57,67)
    private val background=Color.rgb(247,249,251)
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun col()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun space(n:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(n)) }
    private fun shape(fill:Int,radius:Int=13)=GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat()
    }
    private fun txt(value:String,size:Float=14f,bold:Boolean=false,color:Int=ink)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun action(label:String,primary:Boolean=false,onTap:()->Unit)=TextView(activity).apply {
        text=label;gravity=Gravity.CENTER;minHeight=dp(44);textSize=13.5f
        setPadding(dp(8),dp(8),dp(8),dp(8));setTypeface(null,Typeface.BOLD)
        setTextColor(if(primary)Color.WHITE else green)
        background=shape(if(primary)green else 0xFFEAF4EF.toInt(),12)
        setOnClickListener { onTap() }
    }
    private fun entry(value:String,hint:String,numeric:Boolean=false)=EditText(activity).apply {
        setText(value);this.hint=hint;setSingleLine(true);textSize=15f
        if(numeric)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
    }
    private fun field(box:LinearLayout,title:String,view:View) {
        box.addView(txt(title,12f,true,muted));box.addView(space(4));box.addView(view)
        box.addView(space(12))
    }
    private fun picker(options:List<String>)=Spinner(activity).apply {
        adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,options)
    }
    private fun card()=col().apply { setPadding(dp(13),dp(14),dp(13),dp(14));background=shape(Color.WHITE) }
    private fun money(paise:Long):String="₹"+NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits=2;minimumFractionDigits=if(paise%100==0L)0 else 2
    }.format(paise/100.0)
    private fun toPaise(input:String):Long {
        val text=input.trim().replace(",","").removePrefix("₹")
        return BigDecimal(text).multiply(BigDecimal(100)).longValueExact()
    }
    private fun notify(message:String) { Toast.makeText(activity,message,Toast.LENGTH_LONG).show() }
    private fun form():LinearLayout=col().apply { setPadding(dp(20),dp(8),dp(20),dp(10)) }
    private fun scroll(body:LinearLayout)=ScrollView(activity).apply { addView(body) }
    private var launchedFromManpower=false
    fun showForEmployee(employeeId:String, initialTab:Int=0) {
        launchedFromManpower=true
        selectedEmployeeId=employeeId
        selectedTab=initialTab.coerceIn(0,4)
        FinancePayrollStore(activity).use { store ->
            val employee=store.employee(employeeId) ?: error("Employee not found")
            period=minOf(YearMonth.now(),employee.left?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now())
        }
        show()
    }
    fun showCreateEmployee() {
        launchedFromManpower=true
        show()
        addEmployee()
    }
    fun showEditEmployee(employeeId:String) {
        showForEmployee(employeeId)
        FinancePayrollStore(activity).use { store ->
            store.employee(employeeId)?.let { editStaff(it) }
        }
    }
    private fun backToStaff() {
        if(launchedFromManpower) dialog?.dismiss()
        else { selectedEmployeeId=null;selectedTab=0;render() }
    }
    fun show() {
        val d=Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar)
        d.setOnDismissListener { onChanged() }
        d.setOnKeyListener { _,keyCode,event ->
            if(keyCode==KeyEvent.KEYCODE_BACK && selectedEmployeeId!=null) {
                if(event.action==KeyEvent.ACTION_UP) {
                    backToStaff()
                }
                true
            } else false
        }
        dialog=d;d.show();render()
    }
    /** Simple staff directory — grouped by department, as in the approved sketch. */
    private fun render() {
        val d=dialog ?: return
        if(masterMode) { renderEmployeeMaster(d); return }
        try {
            reconcileIssuedSalaryAdvances()
            val employee=selectedEmployeeId?.let { id -> FinancePayrollStore(activity).use { it.employee(id) } }
            if(employee==null) renderStaffList(d) else renderEmployee(d,employee)
        } catch(error:Exception) { notify("Salary review: ${error.message ?: "Unknown error"}") }
    }
    private data class DepartmentColors(val strong:Int,val soft:Int,val border:Int)
    private fun departmentColors(label:String):DepartmentColors = when(label.trim().lowercase()) {
        "management", "administration" -> DepartmentColors(0xFF214A9B.toInt(),0xFFEAF1FF.toInt(),0xFFCADAF7.toInt())
        "kitchen" -> DepartmentColors(0xFF9C510E.toInt(),0xFFFFF1E1.toInt(),0xFFF5D8B5.toInt())
        "housekeeping" -> DepartmentColors(0xFF07786C.toInt(),0xFFE6F7F2.toInt(),0xFFBDE8DB.toInt())
        "marketing and sales", "marketing", "sales" -> DepartmentColors(0xFF6F42A3.toInt(),0xFFF3ECFF.toInt(),0xFFDAC9F6.toInt())
        "front office", "reception" -> DepartmentColors(0xFF1C718C.toInt(),0xFFE7F5FB.toInt(),0xFFBFE2EF.toInt())
        else -> DepartmentColors(0xFF43566F.toInt(),0xFFF0F3F8.toInt(),0xFFD6DFE8.toInt())
    }
    private fun departmentHeading(label:String,count:Int):LinearLayout {
        val palette=departmentColors(label)
        return row().apply {
            setPadding(dp(12),dp(11),dp(12),dp(11))
            background=shape(palette.soft,11)
            addView(View(activity).apply {
                background=shape(palette.strong,4)
            },LinearLayout.LayoutParams(dp(4),dp(26)))
            addView(txt(label,15f,true,palette.strong).apply {
                setPadding(dp(11),0,0,0)
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(txt("$count staff",11f,true,palette.strong))
        }
    }
    private fun renderStaffList(d:Dialog) {
        val employees=FinancePayrollStore(activity).use { it.employees() }
        val root=col().apply { setBackgroundColor(this@FinanceSalaryPlannerScreen.background) }
        root.addView(simpleHeader("Staff",{ d.dismiss() },"+ Staff",{ addEmployee() }))
        val content=col().apply { setPadding(dp(16),dp(9),dp(16),dp(26)) }
        val shown=employees.filter { it.active || it.left==null }
        if(shown.isEmpty())content.addView(txt("No staff yet. Tap + Staff to add your first employee.",14f,false,muted))
        val groups=shown.groupBy { displayDepartment(it.department) }
        val priority=listOf("Management","Kitchen","Housekeeping","Marketing and Sales")
        val ordered=groups.keys.sortedWith(compareBy<String> { if(it in priority) priority.indexOf(it) else 100 }
            .thenBy { it.lowercase() })
        ordered.forEach { department ->
            val staff=groups[department].orEmpty().sortedBy { it.name.lowercase() }
            content.addView(space(14))
            content.addView(departmentHeading(department,staff.size))
            content.addView(space(7))
            staff.forEach { e ->
                val palette=departmentColors(department)
                val entry=row().apply {
                    setPadding(dp(13),dp(12),dp(10),dp(12))
                    background=shape(Color.WHITE,10)
                    addView(View(activity).apply {
                        background=shape(palette.strong,3)
                    },LinearLayout.LayoutParams(dp(3),dp(35)))
                    addView(col().apply {
                        setPadding(dp(10),0,dp(6),0)
                        addView(txt(e.name,15f,true))
                        addView(space(2))
                        addView(txt(e.designation.ifBlank { e.department },12f,false,muted))
                    },LinearLayout.LayoutParams(0,-2,1f))
                    addView(txt("Edit",12f,true,palette.strong).apply {
                        setPadding(dp(9),dp(8),dp(9),dp(8))
                        setOnClickListener { editStaff(e) }
                    })
                    addView(txt("›",23f,false,muted).apply {
                        setPadding(dp(6),dp(2),0,dp(2))
                        setOnClickListener { selectedEmployeeId=e.id;period=minOf(YearMonth.now(),e.left?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now());selectedTab=0;render() }
                    })
                    setOnClickListener { selectedEmployeeId=e.id;period=minOf(YearMonth.now(),e.left?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now()); selectedTab=0;render() }
                }
                content.addView(entry)
                content.addView(space(5))
            }
        }
        val former=employees.filter { !it.active && it.left!=null }
        if(former.isNotEmpty()) {
            content.addView(space(12))
            content.addView(departmentHeading("Former Staff",former.size))
            content.addView(space(6))
            former.forEach { e ->
                content.addView(row().apply {
                    setPadding(dp(12),dp(12),dp(12),dp(12))
                    background=shape(Color.WHITE,10)
                    addView(txt("${e.name} · ${e.designation.ifBlank { e.department }}",13f,false,muted),
                        LinearLayout.LayoutParams(0,-2,1f))
                    addView(txt("›",23f,false,muted))
                    setOnClickListener { selectedEmployeeId=e.id;period=minOf(YearMonth.now(),e.left?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now());selectedTab=0;render() }
                })
                content.addView(space(5))
            }
        }
        val scroller=ScrollView(activity).apply { addView(content) }
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
    }
    private fun displayDepartment(department:String):String = when(department.trim().lowercase()) {
        "administration","management" -> "Management"
        "sales & marketing","marketing","marketing & sales" -> "Marketing and Sales"
        else -> department
    }
    private fun simpleHeader(title:String,onBack:()->Unit,
                             actionLabel:String?=null,onAction:(()->Unit)?=null):LinearLayout = row().apply {
        setPadding(dp(14),dp(10),dp(15),dp(9))
        setBackgroundColor(Color.WHITE)
        addView(txt("‹",28f,false,ink).apply {
            gravity=Gravity.CENTER;setOnClickListener { onBack() }
        },LinearLayout.LayoutParams(dp(35),dp(43)))
        addView(txt(title,20f,true,ink),LinearLayout.LayoutParams(0,-2,1f))
        if(actionLabel!=null && onAction!=null) {
            addView(txt(actionLabel,13f,true,green).apply {
                setPadding(dp(8),dp(10),dp(8),dp(10))
                setOnClickListener { onAction() }
            })
        }
    }
    private fun rule()=View(activity).apply {
        setBackgroundColor(0xFFE2E6E9.toInt())
        layoutParams=LinearLayout.LayoutParams(-1,dp(1))
    }
    private fun payrollData(employee:FinancePayrollStore.Employee):Pair<FinancePayrollStore.Run?,FinanceActivityStore.Bill?> {
        val current=FinancePayrollStore(activity).use { store ->
            store.runs(period.toString()).firstOrNull { it.employeeId==employee.id }
        }
        val bill=current?.takeIf { it.status=="ISSUED" }?.let { run ->
            FinanceActivityStore(activity).use { ledger -> ledger.bill(run.id) }
        }
        return current to bill
    }
    private fun employeeAdvances(employee:FinancePayrollStore.Employee):List<FinanceSalaryAdvancePolicy.Advance> =
        FinanceActivityStore(activity).use { ledger ->
            ledger.payments().filter { it.kind=="ADVANCE" && it.vendorId=="employee:${employee.id}" }
                .map { FinanceSalaryAdvancePolicy.Advance(it.id,it.date,it.amount,it.available) }
        }
    /** Only the date's own payroll month is auto-allocated; older *partially used* advances roll forward. */
    private fun reconcileIssuedSalaryAdvances() {
        val issued=FinancePayrollStore(activity).use { it.issuedRuns().sortedWith(compareBy({ it.period },{ it.employeeId })) }
        if(issued.isEmpty())return
        FinanceActivityStore(activity).use { ledger ->
            ledger.synchronize(includeManual=false)
            issued.forEach { run ->
                val bill=ledger.bill(run.id) ?: return@forEach
                if(bill.due<=0L)return@forEach
                val advances=ledger.payments().filter { it.kind=="ADVANCE" && it.vendorId==bill.vendorId }
                    .map { FinanceSalaryAdvancePolicy.Advance(it.id,it.date,it.amount,it.available) }
                FinanceSalaryAdvancePolicy.plan(YearMonth.parse(run.period),bill.due,advances).forEach { item ->
                    ledger.allocateAdvance(item.paymentId,bill.id,item.amount,LocalDate.now().toString())
                }
            }
        }
    }
    private fun renderEmployee(d:Dialog,e:FinancePayrollStore.Employee) {
        val firstMonth=YearMonth.from(LocalDate.parse(e.joined))
        val lastMonth=e.left?.let { YearMonth.from(LocalDate.parse(it)) }
        if(period.isBefore(firstMonth) || (lastMonth!=null && period.isAfter(lastMonth))) {
            val root=col().apply { setBackgroundColor(Color.WHITE) }
            root.addView(simpleHeader("Salary",{ backToStaff() },"Edit Staff",{ editStaff(e) }))
            root.addView(txt("${e.name} was not employed in $period.",14f,false,muted).apply {
                setPadding(dp(16),dp(20),dp(16),dp(10))
            })
            root.addView(action("Choose Month") { selectMonth() })
            d.setContentView(root)
            d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
            return
        }
        val (run,bill)=payrollData(e)
        val advances=employeeAdvances(e)
        val bonus=FinancePayrollStore(activity).use { it.bonuses(e.id,period.toString()) }
        val bonusAmount=bonus.fold(0L) { sum,b -> Math.addExact(sum,b.amountPaise) }
        val base=FinancePayrollStore(activity).use { it.preview(e,period.toString(),0,0,0).grossPayPaise }
        val gross=if(run?.status=="ISSUED")run.amountPaise else Math.addExact(base,bonusAmount)
        val projection=FinanceSalaryAdvancePolicy.plan(period,gross,advances).sumOf { it.amount }
        val breakdown=bill?.let { FinanceActivityStore(activity).use { ledger -> ledger.salarySettlement(it.id) } }
        val advanced=breakdown?.advancePaise ?: projection
        val settledCash=breakdown?.paidLaterPaise ?: 0L
        val due= bill?.due ?: maxOf(0L,gross-advanced)
        val monthLabel=period.month.name.lowercase().replaceFirstChar { it.uppercase() }+" "+period.year
        val monthSalary=FinancePayrollStore(activity).use { it.salaryForPeriod(e.id,period.toString()) }
        val root=col().apply { setBackgroundColor(Color.WHITE) }
        root.addView(simpleHeader("Salary",{ backToStaff() },"Edit Staff",{ editStaff(e) }))
        val content=col().apply { setPadding(dp(16),dp(5),dp(16),dp(24)) }
        content.addView(row().apply {
            addView(col().apply {
                addView(txt(e.name,18f,true))
                addView(txt(e.designation.ifBlank { e.department },13f,false,muted))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(col().apply {
                gravity=Gravity.END
                addView(txt(money(monthSalary)+"/Month",15f,true).apply { gravity=Gravity.END })
                addView(txt(displayDepartment(e.department),12f,true,departmentColors(displayDepartment(e.department)).strong).apply {
                    gravity=Gravity.END
                })
            })
        })
        content.addView(space(11))
        content.addView(row().apply {
            addView(txt("‹",24f,true,if(period.isAfter(firstMonth))ink else 0xFFC3C5C8.toInt()).apply {
                gravity=Gravity.CENTER
                setOnClickListener { if(period.isAfter(firstMonth)) { period=period.minusMonths(1);render() } }
            },LinearLayout.LayoutParams(dp(46),dp(43)))
            addView(txt(monthLabel+"  ⌄",17f,true).apply {
                gravity=Gravity.CENTER;setOnClickListener { selectMonth() }
            },LinearLayout.LayoutParams(0,dp(43),1f))
            addView(txt("›",24f,true,if(period.isBefore(minOf(YearMonth.now(),lastMonth ?: YearMonth.now())))ink else 0xFFC3C5C8.toInt()).apply {
                gravity=Gravity.CENTER
                setOnClickListener { if(period.isBefore(minOf(YearMonth.now(),lastMonth ?: YearMonth.now()))) { period=period.plusMonths(1);render() } }
            },LinearLayout.LayoutParams(dp(46),dp(43)))
        })
        content.addView(rule());content.addView(space(6))
        val tabs=listOf("Attendance","Advance","Bonus","Earned Leaves","Salary Slip")
        val nav=row()
        tabs.forEachIndexed { i,label ->
            nav.addView(col().apply {
                gravity=Gravity.CENTER
                setOnClickListener { selectedTab=i;render() }
                addView(txt(label,13f,selectedTab==i,if(selectedTab==i)green else muted).apply {
                    gravity=Gravity.CENTER;setPadding(dp(8),dp(10),dp(8),dp(12))
                })
                addView(View(activity).apply {
                    setBackgroundColor(if(selectedTab==i)green else Color.TRANSPARENT)
                    layoutParams=LinearLayout.LayoutParams(-1,dp(3))
                })
            },LinearLayout.LayoutParams(dp(if(label=="Earned Leaves")120 else 105),-2))
        }
        val tabsScroller=HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false
            addView(nav)
        }
        content.addView(tabsScroller);content.addView(space(17))
        when(selectedTab) {
            0->showAttendanceTab(content,e)
            1->showAdvanceTab(content,e,advances)
            2->showBonusTab(content,e,bonus,run)
            3->showLeaveTab(content,e)
            else->showSalaryTab(content,e,run,bill,base,bonusAmount,gross,advanced,settledCash,due)
        }
        val scroller=ScrollView(activity).apply { addView(content) }
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
    }
    private fun simpleLine(parent:LinearLayout,left:String,right:String,rightColor:Int=ink) {
        parent.addView(row().apply {
            setPadding(0,dp(9),0,dp(9))
            addView(txt(left,14f,false,ink),LinearLayout.LayoutParams(0,-2,1f))
            addView(txt(right,14f,true,rightColor))
        })
    }
    /** Tap: present · Double-tap: absent · Long-press: clear. Android confirms a single tap
     * only after the double-tap interval, avoiding an accidental present-first write. */
    private fun showAttendanceTab(parent:LinearLayout,e:FinancePayrollStore.Employee) {
        val m=FinancePayrollStore(activity).use { it.attendanceMonth(e.id,period.toString()) }
        val today=LocalDate.now()
        parent.addView(txt("Attendance · ${period.month.name.lowercase().replaceFirstChar { it.uppercase() }}",17f,true))
        parent.addView(space(7))
        parent.addView(txt("Tap = Present  ·  Double-tap = Absent  ·  Hold = Clear",12f,false,muted))
        parent.addView(space(10))
        val initials=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        parent.addView(row().apply {
            initials.forEach { day ->
                addView(txt(day,11f,true,muted).apply { gravity=Gravity.CENTER },
                    LinearLayout.LayoutParams(0,dp(28),1f))
            }
        })
        val firstOffset=period.atDay(1).dayOfWeek.value-1
        val slots=firstOffset+period.lengthOfMonth()
        val rows=(slots+6)/7
        for(week in 0 until rows) {
            parent.addView(row().apply {
                for(column in 0..6) {
                    val number=week*7+column-firstOffset+1
                    if(number !in 1..period.lengthOfMonth()) {
                        addView(View(activity),LinearLayout.LayoutParams(0,dp(51),1f))
                    } else {
                        val date=period.atDay(number)
                        val enabled=!date.isBefore(m.first) && !date.isAfter(m.last) && !date.isAfter(today)
                        val value=m.days[date]
                        val fill=when {
                            !enabled -> 0xFFF0F2F5.toInt()
                            value=="P" -> 0xFFDFF4E5.toInt()
                            value=="A" -> 0xFFFFE5E8.toInt()
                            else -> 0xFFEAF0F7.toInt()
                        }
                        val textColor=when {
                            !enabled -> 0xFFAEB5C0.toInt()
                            value=="P" -> green
                            value=="A" -> red
                            else -> ink
                        }
                        val cell=txt(number.toString()+(when(value){ "P"->" ✓"; "A"->" ×"; else->"" }),
                            12f,true,textColor).apply {
                            gravity=Gravity.CENTER
                            background=shape(fill,9)
                            contentDescription="${date}: ${when(value) {"P"->"Present";"A"->"Absent";else->"Unmarked"}}"
                            if(enabled) {
                                val detector=GestureDetector(activity,object:GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(event:MotionEvent):Boolean=true
                                    override fun onSingleTapConfirmed(event:MotionEvent):Boolean {
                                        changeAttendance(e,date,"P");return true
                                    }
                                    override fun onDoubleTap(event:MotionEvent):Boolean {
                                        changeAttendance(e,date,"A");return true
                                    }
                                    override fun onLongPress(event:MotionEvent) {
                                        changeAttendance(e,date,null)
                                    }
                                })
                                isClickable=true
                                setOnTouchListener { _,event -> detector.onTouchEvent(event);true }
                            }
                        }
                        addView(cell,LinearLayout.LayoutParams(0,dp(51),1f).apply {
                            setMargins(dp(2),dp(2),dp(2),dp(2))
                        })
                    }
                }
            })
        }
        parent.addView(space(14))
        simpleLine(parent,"Present",m.present.toString(),green)
        simpleLine(parent,"Absent",m.absent.toString(),red)
        val elapsedUnmarked=(m.first.toEpochDay()..minOf(today,m.last).toEpochDay()).count { day ->
            LocalDate.ofEpochDay(day) !in m.days
        }
        simpleLine(parent,"Unmarked through today",elapsedUnmarked.toString(),if(elapsedUnmarked>0)red else green)
        parent.addView(space(9))
        parent.addView(action("Mark unmarked elapsed days Present",true) {
            val pending=elapsedUnmarked
            if(pending==0) { notify("All elapsed days are marked");return@action }
            AlertDialog.Builder(activity).setTitle("Mark $pending days present?")
                .setMessage("Only unmarked days through today will be marked Present. Existing absences will not be changed. Confirm only after checking the attendance register.")
                .setNegativeButton("Cancel",null).setPositiveButton("Mark Present") { _,_->
                    try {
                        val updated=FinancePayrollStore(activity).use { it.markUnmarkedPresent(e.id,period.toString()) }
                        notify("$updated days marked Present")
                        render()
                    } catch(error:Exception) { notify(error.message?:"Could not mark attendance") }
                }.show()
        })
        parent.addView(space(13))
        parent.addView(txt(
            if(!m.closed) "This month is ongoing. Company leave credits will be calculated at month-end once all employed days are marked."
            else if(m.unmarked>0) "Month-end credits pending: ${m.unmarked} employed day(s) still unmarked. No automatic leave credit yet."
            else "Month complete. Earned Leaves are calculated automatically; no manual leave entry is needed.",
            12f,false,muted))
    }
    private fun changeAttendance(e:FinancePayrollStore.Employee,date:LocalDate,value:String?) {
        val month=YearMonth.from(date).toString()
        val current=FinancePayrollStore(activity).use { it.attendanceMonth(e.id,month) }
        val old=current.days[date]
        if(old==value) return
        val apply:(String)->Unit={ reason ->
            try {
                FinancePayrollStore(activity).use { it.setAttendanceDay(e.id,date.toString(),value,reason) }
                render()
            } catch(error:Exception) { notify(error.message?:"Attendance could not be changed") }
        }
        if(current.closed && old!=null) {
            val input=entry("","Reason for correcting a closed month")
            val alert=AlertDialog.Builder(activity)
                .setTitle("Correct attendance · $date")
                .setMessage("This month is closed. A reason is required to preserve the payroll audit history.")
                .setView(input).setNegativeButton("Cancel",null)
                .setPositiveButton("Save correction",null).create()
            alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason=input.text.toString().trim()
                if(reason.length<5) { notify("Enter at least 5 characters for the reason") }
                else { alert.dismiss();apply(reason) }
            } };alert.show()
        } else apply("")
    }

    private fun showAdvanceTab(parent:LinearLayout,e:FinancePayrollStore.Employee,
                               advances:List<FinanceSalaryAdvancePolicy.Advance>) {
        parent.addView(row().apply {
            addView(txt("Advances · ${period}",16f,true),LinearLayout.LayoutParams(0,-2,1f))
            addView(txt("+ Add",14f,true,green).apply { setOnClickListener { payAdvance(e) } })
        });parent.addView(space(10));parent.addView(rule())
        val current=advances.filter { YearMonth.from(LocalDate.parse(it.date))==period }.sortedByDescending { it.date }
        if(current.isEmpty())parent.addView(txt("No advance recorded for this month.",13f,false,muted).apply {
            setPadding(0,dp(15),0,dp(15))
        })
        current.forEach { a -> simpleLine(parent,a.date,money(a.amount)) }
        parent.addView(rule());parent.addView(space(11))
        simpleLine(parent,"Total advance in month",money(current.sumOf { it.amount }))
        val carry=FinanceSalaryAdvancePolicy.eligible(period,advances).filter { YearMonth.parse(it.date.substring(0,7))!=period }
        if(carry.isNotEmpty()) {
            simpleLine(parent,"Earlier advance carried forward",money(carry.sumOf { it.available }))
        }
        parent.addView(space(12))
        parent.addView(txt("Advances recorded in this month reduce this month's salary automatically when it is issued. No second cash payment is created.",12f,false,muted))
        parent.addView(space(12))
        parent.addView(txt("View / correct advance entries  ›",13f,true,green).apply {
            setOnClickListener { showEmployeeAdvances(e) }
        })
    }
    private fun showBonusTab(parent:LinearLayout,e:FinancePayrollStore.Employee,
                             bonuses:List<FinancePayrollStore.Bonus>,run:FinancePayrollStore.Run?) {
        parent.addView(row().apply {
            addView(txt("Bonus · ${period}",16f,true),LinearLayout.LayoutParams(0,-2,1f))
            if(run?.status!="ISSUED")addView(txt("+ Add",14f,true,green).apply { setOnClickListener { addBonus(e) } })
        });parent.addView(space(11));parent.addView(rule())
        if(bonuses.isEmpty())parent.addView(txt("No bonus for this month.",13f,false,muted).apply {
            setPadding(0,dp(15),0,dp(15))
        })
        bonuses.forEach { bonus ->
            val r=row().apply {
                setPadding(0,dp(10),0,dp(10))
                addView(txt(bonus.label+" · "+bonus.date,13f),LinearLayout.LayoutParams(0,-2,1f))
                addView(txt("+"+money(bonus.amountPaise),14f,true,green))
                if(run?.status!="ISSUED")setOnClickListener { cancelBonus(bonus) }
            }
            parent.addView(r)
        }
        parent.addView(rule())
        simpleLine(parent,"Total bonus",money(bonuses.sumOf { it.amountPaise }),green)
        if(run?.status=="ISSUED")parent.addView(txt("Issued salary cannot be changed; any correction requires an approved adjustment.",12f,false,muted))
    }
    /** Read-only leave bank. Daily attendance is the ONLY input; a completed month is auto-confirmed. */
    private fun showLeaveTab(parent:LinearLayout,e:FinancePayrollStore.Employee) {
        val joined=LocalDate.parse(e.joined)
        val leaving=e.left?.let(LocalDate::parse)
        val last=minOf(period,YearMonth.now(),leaving?.let(YearMonth::from) ?: YearMonth.now())
        val months=FinancePayrollStore(activity).use { store ->
            store.reconcileCompletedAttendance(e.id)
            store.leaveMonths(e.id).associateBy { it.period }
        }
        parent.addView(txt("Earned Leaves",16f,true))
        parent.addView(space(4))
        parent.addView(txt("Calculated automatically from the Attendance calendar. No manual entries.",12f,false,muted))
        parent.addView(space(12));parent.addView(rule())
        var bank=0
        var pending=0
        var current=YearMonth.from(joined)
        while(!current.isAfter(last)) {
            val record=months[current.toString()]
            val confirmed=record?.confirmed==true && record.note.startsWith("AUTO_ATTENDANCE:")
            val finished=!minOf(current.atEndOfMonth(),leaving?.takeIf { YearMonth.from(it)==current } ?: current.atEndOfMonth()).isAfter(LocalDate.now())
            val changed=if(confirmed)FinanceLeaveBankPolicy.month(current,joined,leaving,e.restAllowance,
                record!!.nonWorking,record.approvedExtraPaid) else null
            if(changed!=null)bank+=changed.netChange else pending++
            val label=current.month.name.lowercase().replaceFirstChar { it.uppercase() }+" "+current.year
            val detail=if(changed!=null)(if(changed.netChange>=0)"+" else "")+changed.netChange+" credits"
                else if(finished)"Attendance pending" else "In progress"
            simpleLine(parent,label,detail,if(changed!=null)green else muted)
            current=current.plusMonths(1)
        }
        parent.addView(rule());parent.addView(space(9))
        simpleLine(parent,"Company leave bank",bank.toString(),if(bank>=0)green else red)
        if(pending>0)parent.addView(txt("$pending month(s) awaiting completed attendance. Unmarked dates do not earn automatic credits.",12f,false,muted))
        parent.addView(space(9))
        parent.addView(txt("This is the discretionary company leave-credit balance, not statutory earned leave or weekly rest. Regular salary remains separate, and previously issued payroll is not recalculated.",12f,false,muted))
    }
    private fun showSalaryTab(parent:LinearLayout,e:FinancePayrollStore.Employee,
                              run:FinancePayrollStore.Run?,bill:FinanceActivityStore.Bill?,
                              base:Long,bonus:Long,gross:Long,advance:Long,cash:Long,due:Long) {
        parent.addView(txt("Salary Slip · ${period}",16f,true))
        parent.addView(space(10));parent.addView(rule())
        val shownBase=if(run?.status=="ISSUED")run.amountPaise-run.adjustmentPaise else base
        val shownBonus=if(run?.status=="ISSUED")run.adjustmentPaise else bonus
        simpleLine(parent,"Basic Salary",money(shownBase))
        simpleLine(parent,"Advance",if(advance>0L)"−"+money(advance) else money(0L),if(advance>0L)red else ink)
        val bonusLabel=if(run?.status=="ISSUED" && shownBonus!=0L && bonus==0L)
            "Prior approved adjustment" else "Bonus"
        simpleLine(parent,bonusLabel,when {
            shownBonus>0L -> "+"+money(shownBonus)
            shownBonus<0L -> "−"+money(-shownBonus)
            else -> money(0L)
        },if(shownBonus>0L)green else if(shownBonus<0L)red else ink)
        if(cash>0L)simpleLine(parent,"Salary already paid","−"+money(cash),red)
        parent.addView(rule());parent.addView(space(10))
        simpleLine(parent,"Grand Total",money(due),green)
        val monthEnd=period.atEndOfMonth()
        val finalDay=e.left?.let(LocalDate::parse)?.takeIf { YearMonth.from(it)==period }
        val closable=!(finalDay ?: monthEnd).isAfter(LocalDate.now())
        parent.addView(space(16))
        parent.addView(action("Pay Now",true) {
            when {
                bill!=null && bill.due<=0L -> notify("Salary already settled for $period")
                bill!=null -> settleSalary(e,bill.id)
                !closable -> notify("Month closes ${monthEnd}. Record salary advances until then.")
                else -> issueAndPay(e,run)
            }
        })
        parent.addView(space(12))
        parent.addView(txt(when {
            bill!=null -> "Issued · Due ${bill.dueDate} · Settled ${money(bill.paid)}"
            closable -> "Preview only · Pay Now will request salary issuance first"
            else -> "Running month · Salary cannot be issued before month-end"
        },12f,false,muted))
        parent.addView(space(12))
        parent.addView(txt("Employee ledger  ›",13f,true,green).apply {
            setOnClickListener { FinanceUnifiedStatementScreen(activity,onChanged).openPayeeLedger("employee:${e.id}") }
        })
        parent.addView(space(10))
        if(run?.status=="ISSUED")parent.addView(txt("View dated salary payments  ›",12f,false,muted).apply {
            setOnClickListener { showEmployeePayments(e) }
        })
        if(e.left==null)parent.addView(txt("Final settlement  ›",12f,false,muted).apply {
            setPadding(0,dp(12),0,dp(5));setOnClickListener { exitEmployee(e) }
        })
        parent.addView(space(15))
        parent.addView(txt("Salary expense includes basic pay and approved bonus. Advances are prior cash paid, not an additional expense. Company leave credits settle separately at exit.",11f,false,muted))
    }
    private fun issueAndPay(e:FinancePayrollStore.Employee,existing:FinancePayrollStore.Run?) {
        try {
            val salary=FinancePayrollStore(activity).use { p ->
                val extras=p.bonusTotal(e.id,period.toString())
                p.preview(e,period.toString(),0,0,extras)
            }
            FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(includeManual=false)
                val duplicate=ledger.bills().any { b ->
                    b.source=="FIXED" && b.period==period.toString() &&
                    b.vendor.trim().equals(e.name.trim(),true) &&
                    (b.title.contains("salary",true) || b.title.contains("wage",true))
                }
                require(!duplicate) { "Existing recurring salary bill for ${e.name}; stop the recurring salary to avoid duplicate payroll." }
            }
            AlertDialog.Builder(activity).setTitle("Issue monthly salary?")
                .setMessage("${e.name} · $period\nBasic ${money(salary.basePayPaise)}\nBonus ${money(salary.adjustmentPaise)}\nGross salary ${money(salary.grossPayPaise)}\n\nAdvances from this month will be automatically applied once, and you can pay only the remaining amount.\n\nSalary bills cannot be edited after issuance.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Issue & Continue") { _,_ ->
                    try {
                        val issued=FinancePayrollStore(activity).use { p ->
                            val r=p.saveDraft(e.id,period.toString(),0,0,
                                p.bonusTotal(e.id,period.toString()),
                                "Monthly basic salary with recorded bonus; leave credits separate")
                            p.issue(r.id)
                        }
                        reconcileIssuedSalaryAdvances()
                        val issuedBill=FinanceActivityStore(activity).use { it.bill(issued.id) }
                        render()
                        if(issuedBill!=null && issuedBill.due>0L) settleSalary(e,issued.id)
                        else notify("Salary fully settled by advances; excess carries forward where applicable.")
                    } catch(error:Exception) { notify("Salary issue: ${error.message}") }
                }.show()
        } catch(error:Exception) { notify(error.message?:"Unable to prepare salary") }
    }
    private fun addBonus(e:FinancePayrollStore.Employee) {
        val form=form()
        val title=entry("Performance Bonus","Bonus description")
        val date=entry(if(period==YearMonth.now())LocalDate.now().toString() else period.atEndOfMonth().toString(),"YYYY-MM-DD")
        val amount=entry("","Bonus amount ₹",true)
        val reason=entry("","Manager approval / reason")
        field(form,"BONUS TYPE",title);field(form,"DATE IN THIS MONTH",date)
        field(form,"AMOUNT",amount);field(form,"MANAGER REASON",reason)
        val alert=AlertDialog.Builder(activity).setTitle("Add Bonus · ${e.name}")
            .setView(scroll(form)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                require(YearMonth.from(LocalDate.parse(date.text.toString()))==period) { "Select a date within $period" }
                FinancePayrollStore(activity).use { it.addBonus(e.id,date.text.toString(),
                    title.text.toString(),toPaise(amount.text.toString()),reason.text.toString()) }
                alert.dismiss();render()
            } catch(error:Exception) { notify(error.message?:"Cannot save bonus") }
        } };alert.show()
    }
    private fun cancelBonus(bonus:FinancePayrollStore.Bonus) {
        val reason=entry("","Reason to cancel this bonus")
        val alert=AlertDialog.Builder(activity).setTitle("Cancel ${bonus.label}?")
            .setView(reason).setNegativeButton("Back",null).setPositiveButton("Cancel Bonus",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinancePayrollStore(activity).use { it.voidBonus(bonus.id,reason.text.toString()) }
                alert.dismiss();render()
            } catch(error:Exception) { notify(error.message?:"Unable to cancel bonus") }
        }};alert.show()
    }
    /** The ONLY UI entry for creating/updating the employee master. Payroll processing stays separate. */
    private fun renderEmployeeMaster(d:Dialog) {
        try {
            val employees=FinancePayrollStore(activity).use { it.employees() }
            val root=col().apply { setBackgroundColor(this@FinanceSalaryPlannerScreen.background) }
            root.addView(row().apply {
                setPadding(dp(14),dp(17),dp(14),dp(12));setBackgroundColor(Color.WHITE)
                addView(action("‹") { d.dismiss() },LinearLayout.LayoutParams(dp(45),dp(44)))
                addView(col().apply {
                    addView(txt("Employee Master",21f,true))
                    addView(txt("Finance Settings  ·  One staff directory",12f,false,muted))
                },LinearLayout.LayoutParams(0,-2,1f))
            })
            val content=col().apply { setPadding(dp(14),dp(14),dp(14),dp(28)) }
            content.addView(action("+ Add Employee",true) { addEmployee() })
            content.addView(space(13))
            content.addView(txt("${employees.size} employee record(s)  ·  Salary expense type: Salaries & Wages (HE0101)",12f,false,muted))
            content.addView(space(12))
            if(employees.isEmpty()) content.addView(txt("No employees created. Add staff once here; Manpower will use the same records.",13f,false,muted))
            employees.forEach { e ->
                content.addView(card().apply {
                    addView(row().apply {
                        addView(col().apply {
                            addView(txt(e.name,16f,true))
                            addView(txt("${e.designation.ifBlank { e.department }} · ${e.department} · ${e.property}",12f,false,muted))
                        },LinearLayout.LayoutParams(0,-2,1f))
                        addView(txt(money(e.monthlyPaise)+"/mo",13f,true,green))
                    })
                    addView(space(8))
                    addView(txt("Edit Staff · Joining Date · Salary  ›",13f,true,green).apply {
                        setPadding(0,dp(7),0,dp(7));setOnClickListener { editStaff(e) }
                    })
                    addView(space(6))
                    addView(txt("Paid rest allowance: ${e.restAllowance} · Salary due: day ${e.payDay} next month",12f,false,muted))
                    val exit=FinancePayrollStore(activity).use { it.exitReview(e.id) }
                    if(exit!=null) {
                        addView(space(6))
                        addView(txt("Exit reviewed ${exit.lastDay} · Company benefit ${money(exit.companyAmountPaise)}",12f,false,muted))
                    } else if(e.left!=null) addView(txt("Left employment: ${e.left} · statutory dues require separate review",12f,false,muted))
                    else {
                        addView(space(9))
                        addView(txt("Exit & final settlement review  ›",12f,true,green).apply {
                            setOnClickListener { exitEmployee(e) }
                        })
                    }
                })
                content.addView(space(9))
            }
            content.addView(space(12))
            content.addView(txt("Staff edits require a reason and are audited. Monthly salary changes start from the chosen effective month; previously issued salary bills remain unchanged.",11f,false,muted))
            val scroller=ScrollView(activity).apply { addView(content) }
            root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
            d.setContentView(root)
            d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
        } catch(e:Exception) { notify("Employee master review: ${e.message}") }
    }
    /** Edit staff from either the Staff directory, employee detail or Employee Master. */
    private fun editStaff(e:FinancePayrollStore.Employee) {
        val body=form()
        val name=entry(e.name,"Employee name")
        val designation=entry(e.designation.ifBlank { e.department },"Chef, Manager, Helper ...")
        val depts=(FinanceGlobalCatalogDefaults.departments+e.department).distinct()
        val dept=picker(depts).apply {
            val idx=depts.indexOf(e.department)
            if(idx>=0)setSelection(idx)
        }
        val property=entry(e.property,"Hotel / property")
        val joined=entry(e.joined,"YYYY-MM-DD")
        val salary=entry(BigDecimal.valueOf(e.monthlyPaise,2).toPlainString(),"Monthly salary ₹",true)
        val suggested=FinancePayrollStore(activity).use { store ->
            val lastIssue=store.lastIssuedMonth(e.id)
            val latestRate=store.latestRateMonth(e.id)
            if(lastIssue==null)latestRate ?: YearMonth.from(LocalDate.parse(e.joined))
            else maxOf(YearMonth.now(),lastIssue.plusMonths(1),latestRate ?: YearMonth.from(LocalDate.parse(e.joined)))
        }
        val effective=entry(suggested.toString(),"YYYY-MM")
        val reason=entry("","Reason for correction (required)")
        field(body,"EMPLOYEE NAME",name)
        field(body,"DESIGNATION",designation)
        field(body,"DEPARTMENT",dept)
        field(body,"HOTEL / PROPERTY",property)
        field(body,"DATE OF JOINING · YYYY-MM-DD",joined)
        field(body,"MONTHLY SALARY · ₹",salary)
        field(body,"NEW SALARY EFFECTIVE FROM · YYYY-MM",effective)
        field(body,"WHY ARE YOU EDITING?",reason)
        body.addView(txt("Joining-date changes are checked against issued salary and confirmed leave. If salary changes, it applies from the effective month. Issued payslips cannot be rewritten; changing a salary draft may require regenerating it.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("Edit Staff · ${e.name}")
            .setView(scroll(body)).setNegativeButton("Cancel",null)
            .setPositiveButton("Save Changes",null).create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val correctedJoin=LocalDate.parse(joined.text.toString().trim())
                    if(correctedJoin!=LocalDate.parse(e.joined)) {
                        val previousCash=FinanceActivityStore(activity).use { ledger ->
                            ledger.payments().filter { payment ->
                                payment.vendorId=="employee:${e.id}" &&
                                    (payment.kind=="ADVANCE" || payment.kind=="PAYMENT")
                            }.map { LocalDate.parse(it.date) }.minOrNull()
                        }
                        require(previousCash==null || !correctedJoin.isAfter(previousCash)) {
                            "Joining date falls after an existing salary payment or advance. Accountant review required."
                        }
                    }
                    FinancePayrollStore(activity).use { store ->
                        store.editEmployee(e.id,name.text.toString(),designation.text.toString(),
                            depts[dept.selectedItemPosition],property.text.toString(),
                            joined.text.toString(),toPaise(salary.text.toString()),
                            effective.text.toString(),reason.text.toString())
                    }
                    alert.dismiss()
                    notify("Staff details saved · audit reason recorded")
                    render()
                } catch(error:Exception) { notify(error.message ?: "Check staff details") }
            }
        }
        alert.show()
    }

    private fun editDesignation(e:FinancePayrollStore.Employee) {
        val body=form()
        val designation=entry(e.designation,"Chef, Manager, Helper, UT ...")
        val reason=entry("","Reason for changing designation")
        field(body,"DESIGNATION",designation);field(body,"CHANGE REASON",reason)
        val alert=AlertDialog.Builder(activity).setTitle("Designation · ${e.name}")
            .setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinancePayrollStore(activity).use { it.changeDesignation(e.id,designation.text.toString(),reason.text.toString()) }
                alert.dismiss();render()
            } catch(error:Exception) { notify(error.message?:"Unable to update designation") }
        } };alert.show()
    }
    private fun selectMonth() {
        val input=entry(period.toString(),"YYYY-MM")
        AlertDialog.Builder(activity).setTitle("Payroll month").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Open") { _,_->
                try {
                    val target=YearMonth.parse(input.text.toString().trim())
                    require(!target.isAfter(YearMonth.now())) { "Future salary months are unavailable" }
                    val employee=selectedEmployeeId?.let { FinancePayrollStore(activity).use { store -> store.employee(it) } }
                    if(employee!=null) {
                        require(!target.isBefore(YearMonth.from(LocalDate.parse(employee.joined)))) { "Cannot select before joining month" }
                        require(employee.left==null || !target.isAfter(YearMonth.from(LocalDate.parse(employee.left)))) { "Cannot select after departure month" }
                    }
                    period=target;render()
                } catch(e:Exception){notify(e.message?:"Use YYYY-MM")}
            }.show()
    }
    private fun addEmployee() {
        val body=form()
        val name=entry("","Employee name")
        val designation=entry("","Designation, e.g. Chef")
        val depts=FinanceGlobalCatalogDefaults.departments
        val dept=picker(depts)
        val property=entry("Demo Property 01","Hotel / property")
        val salary=entry("","Monthly salary ₹",true)
        val joined=entry(LocalDate.now().toString(),"YYYY-MM-DD")
        val rest=entry("4","Paid rest days per month",true)
        val day=entry("2","Disbursement day of next month",true)
        field(body,"STAFF NAME",name);field(body,"DESIGNATION",designation)
        field(body,"DEPARTMENT",dept);field(body,"PROPERTY",property)
        field(body,"MONTHLY AGREED SALARY",salary);field(body,"START DATE",joined)
        field(body,"MONTHLY DISCRETIONARY COMPANY LEAVE CREDITS",rest)
        field(body,"SALARY PAYMENT DAY",day)
        body.addView(txt("Statutory weekly rest and earned leave are managed separately. Use Exit Review at departure.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("New Employee").setView(scroll(body))
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinancePayrollStore(activity).use { it.addEmployee(name.text.toString(),
                    depts[dept.selectedItemPosition],property.text.toString(),
                    toPaise(salary.text.toString()),joined.text.toString().trim(),
                    null,rest.text.toString().toInt(),
                    day.text.toString().toInt(),designation.text.toString()) }
                alert.dismiss();render()
            } catch(e:Exception){ notify(e.message?:"Check staff details") }
        } };alert.show()
    }
    /** Regular salary is independent of company leave balances. */
    private fun prepareBasicSalary(e:FinancePayrollStore.Employee,existing:FinancePayrollStore.Run?) {
        AlertDialog.Builder(activity).setTitle("Prepare basic salary · ${e.name}")
            .setMessage("${period} · Monthly contracted salary ${money(FinancePayrollStore(activity).use { it.salaryForPeriod(e.id,period.toString()) })}.\nJoining/departure month is prorated by actual calendar days.\n\nLeave-bank entries are not deducted.\n\n${if(existing!=null) "This will replace the unissued draft calculation (reason audited)." else "Create a new salary draft."}")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Prepare") { _,_ ->
                try {
                    FinancePayrollStore(activity).use { it.saveDraft(e.id,period.toString(),0,0,
                        it.bonusTotal(e.id,period.toString()),
                        "Fixed basic salary + approved bonus; leave balance separately tracked" ) }
                    render()
                } catch(x:Exception){notify(x.message?:"Cannot prepare basic salary")}
            }.show()
    }

    private fun issue(e:FinancePayrollStore.Employee,r:FinancePayrollStore.Run) {
        try {
            FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(includeManual=false)
                val duplicates=ledger.bills().filter { bill ->
                    bill.source=="FIXED" && bill.period==r.period &&
                    bill.vendor.trim().equals(e.name.trim(),ignoreCase=true) &&
                    (bill.title.contains("salary",true) || bill.title.contains("wage",true))
                }
                require(duplicates.isEmpty()) {
                    "A recurring salary bill already exists for ${e.name} in ${r.period}. Do not duplicate; stop the old recurring schedule for future months."
                }
            }
            AlertDialog.Builder(activity).setTitle("Issue salary · ${e.name}?")
                .setMessage("${r.period} salary: ${money(r.amountPaise)}\nDue: ${r.dueDate}\n\nIssued salary becomes a fixed bill in the shared Finance Ledger. Its amount cannot be edited. Check the preview and approval before issuing.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Issue Bill") { _,_->
                    try {
                        FinancePayrollStore(activity).use { it.issue(r.id) }
                        FinanceActivityStore(activity).use { it.synchronize(includeManual=false) }
                        reconcileIssuedSalaryAdvances()
                        render()
                    } catch(x:Exception){notify(x.message?:"Salary issue requires review")}
                }.show()
        } catch(x:Exception){notify(x.message?:"Salary issue blocked")}
    }
    private fun payslip(e:FinancePayrollStore.Employee,r:FinancePayrollStore.Run,b:FinanceActivityStore.Bill?) {
        AlertDialog.Builder(activity).setTitle("Salary · ${e.name} · ${r.period}")
            .setMessage("Month: ${r.period}\nCovered employment days: ${r.covered}\nIssued salary: ${money(r.amountPaise)}\nPayments / advances allocated: ${money(b?.paid?:0L)}\nOutstanding: ${money(b?.due?:r.amountPaise)}\nDue date: ${r.dueDate}\n\nCompany leave bank is tracked separately and does not change this issued bill.\n\nNote: ${r.reason.ifBlank { "None" }}\n\nDEV preview only; not a statutory payslip.")
            .setPositiveButton("Close",null).show()
    }
    private fun exitEmployee(e:FinancePayrollStore.Employee) {
        val body=form()
        val last=entry(e.left ?: LocalDate.now().toString(),"Last working date YYYY-MM-DD")
        val noticeChoices=listOf("Notice completed","Notice not completed")
        val notice=picker(noticeChoices)
        val reason=entry("","Mandatory exit / notice approval reference")
        field(body,"LAST WORKING DAY",last)
        field(body,"MANDATORY NOTICE STATUS",notice)
        field(body,"REASON / APPROVAL",reason)
        body.addView(txt("Only discretionary COMPANY benefit can lapse under an approved policy when notice is not served. Approval creates a local company-benefit bill if positive. Statutory earned leave, final wages and other legal dues must still be assessed and paid separately.",12f,false,red))
        body.addView(space(9))
        body.addView(action("Preview final settlement") {
            try {
                val p=FinancePayrollStore(activity).use { it.previewExit(e.id,last.text.toString().trim(),notice.selectedItemPosition==0) }
                AlertDialog.Builder(activity).setTitle("Final Settlement Preview · ${e.name}")
                    .setMessage("Tracked from: ${p.firstTrackedMonth?:"No records"}\nUnconfirmed months: ${if(p.missingMonths.isEmpty())"None" else p.missingMonths.joinToString()}\n\nCompany credits earned: ${p.balance.earned}\nCredits consumed: ${p.balance.used}\nUnused: ${p.balance.unused}\nExcess for review: ${p.balance.unresolvedExtra}\nCompany net balance: ${p.balance.net}\n\nNotice completed: ${p.estimate.noticeCompleted}\nDiscretionary benefit days: ${p.estimate.companyBenefitDays}\nBenefit preview: ${money(p.estimate.companyBenefitPaise)}\n\nStatutory earned leave, unpaid wages, notice recovery and other terminal entitlements require separate legal/accountant review. This preview makes no payment; confirming the exit records a separate company-only benefit bill if positive.")
                    .setPositiveButton("Close",null).show()
            } catch(x:Exception){notify(x.message?:"Cannot preview settlement")}
        })
        val alert=AlertDialog.Builder(activity).setTitle("Exit Review · ${e.name}")
            .setView(scroll(body)).setNegativeButton("Cancel",null)
            .setPositiveButton("Approve Exit & Company Benefit",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val out=FinancePayrollStore(activity).use { it.recordExit(e.id,last.text.toString().trim(),
                    notice.selectedItemPosition==0,reason.text.toString()) }
                alert.dismiss()
                notify("Exit reviewed. Company benefit: ${money(out.companyAmountPaise)}. Statutory dues must be checked.")
                render()
            } catch(x:Exception){notify(x.message?:"Exit review blocked")}
        } };alert.show()
    }
    private fun payAdvance(e:FinancePayrollStore.Employee) {
        val body=form();val amount=entry("","Amount ₹",true)
        val date=entry(LocalDate.now().toString(),"YYYY-MM-DD")
        val methods=listOf("Bank","UPI","Cash","Other");val method=picker(methods)
        val reference=entry("","Bank/UTR reference (recommended)")
        field(body,"ADVANCE AMOUNT",amount);field(body,"ACTUAL PAYMENT DATE",date)
        field(body,"METHOD",method);field(body,"REFERENCE",reference)
        body.addView(txt("Advance belongs to its actual payment month and automatically reduces that month's issued salary. It is not a second salary expense.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("Salary advance · ${e.name}")
            .setView(scroll(body)).setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val enteredDate=date.text.toString().trim()
                require(YearMonth.from(LocalDate.parse(enteredDate))==period) {
                    "Advance date must be within selected month $period. Change month first."
                }
                FinanceActivityStore(activity).use { ledger ->
                    ledger.advance("employee:${e.id}",e.name,toPaise(amount.text.toString()),
                        enteredDate,methods[method.selectedItemPosition],reference.text.toString())
                }
                reconcileIssuedSalaryAdvances()
                alert.dismiss();render()
            } catch(x:Exception){notify(x.message?:"Unable to record salary advance")}
        } };alert.show()
    }
    private fun showEmployeePayments(e:FinancePayrollStore.Employee) {
        val recorded=FinanceActivityStore(activity).use { ledger ->
            ledger.payments().filter { it.kind=="PAYMENT" && it.vendorId=="employee:${e.id}" }
        }
        if(recorded.isEmpty()) { notify("No active dated salary payments for ${e.name}");return }
        AlertDialog.Builder(activity).setTitle("Salary payments · ${e.name}")
            .setItems(recorded.map { "${it.date} · ${money(it.amount)} · ${it.method}" }.toTypedArray()) { _,index ->
                val p=recorded[index]
                val reason=entry("","Mandatory reason for erroneous payment entry")
                val box=form().apply {
                    addView(txt("Only reverse an incorrect local entry; no bank refund is performed. Original record and reason will remain in the audit history.",12f,false,muted))
                    addView(space(8));addView(reason)
                }
                val alert=AlertDialog.Builder(activity).setTitle("Reverse recorded salary payment?")
                    .setView(box).setNegativeButton("Cancel",null).setPositiveButton("Reverse",null).create()
                alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        FinanceActivityStore(activity).use { it.reversePayment(p.id,reason.text.toString()) }
                        alert.dismiss();render()
                    } catch(x:Exception){notify(x.message?:"Cannot reverse payment")}
                } };alert.show()
            }.setNegativeButton("Close",null).show()
    }
    private fun showEmployeeAdvances(e:FinancePayrollStore.Employee) {
        val advances=FinanceActivityStore(activity).use { ledger ->
            ledger.payments().filter { it.kind=="ADVANCE" && it.vendorId=="employee:${e.id}" }
        }
        if(advances.isEmpty()) { notify("No active advances recorded for ${e.name}");return }
        AlertDialog.Builder(activity).setTitle("Advances · ${e.name}")
            .setItems(advances.map { "${it.date} · ${money(it.available)} available of ${money(it.amount)} · ${it.method}" }.toTypedArray()) { _,index ->
                val p=advances[index]
                AlertDialog.Builder(activity).setTitle("Advance · ${money(p.amount)}")
                    .setMessage("${p.date} · ${p.method} · ${p.reference}\nAvailable ${money(p.available)}\n\nAn advance can only be reversed when none of it has been allocated. Reversal does not refund money.")
                    .setNegativeButton("Close",null)
                    .setPositiveButton("Reverse incorrect entry") { _,_->
                        val reason=entry("","Why was this recorded incorrectly?")
                        val alert=AlertDialog.Builder(activity).setTitle("Audited advance reversal")
                            .setView(reason).setNegativeButton("Cancel",null).setPositiveButton("Reverse",null).create()
                        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            try {
                                FinanceActivityStore(activity).use { it.reversePayment(p.id,reason.text.toString()) }
                                alert.dismiss();render()
                            } catch(x:Exception){ notify(x.message?:"Cannot reverse advance") }
                        } };alert.show()
                    }.show()
            }.setNegativeButton("Close",null).show()
    }
    private fun settleSalary(e:FinancePayrollStore.Employee,billId:String,kind:String="salary") {
        val ledgerBill=FinanceActivityStore(activity).use { it.bill(billId) }
            ?:run { notify("Issued salary bill unavailable");return }
        val body=form();val amount=entry(BigDecimal.valueOf(ledgerBill.due,2).toPlainString(),"Amount ₹",true)
        val date=entry(LocalDate.now().toString(),"YYYY-MM-DD")
        val methods=listOf("Bank","UPI","Cash","Other");val method=picker(methods)
        val ref=entry("","Payment reference")
        field(body,"AMOUNT",amount);field(body,"ACTUAL PAYMENT DATE",date)
        field(body,"METHOD",method);field(body,"REFERENCE",ref)
        val alert=AlertDialog.Builder(activity).setTitle("Pay $kind · ${e.name}")
            .setView(body).setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinanceActivityStore(activity).use { ledger ->
                    ledger.payBill(billId,toPaise(amount.text.toString()),date.text.toString().trim(),
                        methods[method.selectedItemPosition],ref.text.toString())
                }
                alert.dismiss();render()
            } catch(x:Exception){notify(x.message?:"Cannot record payment")}
        } };alert.show()
    }
    private fun useAdvance(e:FinancePayrollStore.Employee,billId:String) {
        val list=FinanceActivityStore(activity).use { it.payments().filter { p ->
            p.kind=="ADVANCE" && p.vendorId=="employee:${e.id}" && p.available>0L
        } }
        if(list.isEmpty()){notify("No unallocated advances for this employee");return}
        AlertDialog.Builder(activity).setTitle("Select salary advance")
            .setItems(list.map { "${it.date} · ${money(it.available)} available · ${it.method}" }.toTypedArray()) { _,idx ->
                val payment=list[idx]
                val due=FinanceActivityStore(activity).use { it.bill(billId)?.due?:0L }
                if(due<=0L){notify("Salary already fully settled");return@setItems}
                val amount=entry(BigDecimal.valueOf(minOf(due,payment.available),2).toPlainString(),"Amount ₹",true)
                val alert=AlertDialog.Builder(activity).setTitle("Apply advance without new cash payment")
                    .setView(amount).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
                alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        FinanceActivityStore(activity).use { it.allocateAdvance(payment.id,billId,toPaise(amount.text.toString()),LocalDate.now().toString()) }
                        alert.dismiss();render()
                    } catch(x:Exception){notify(x.message?:"Advance allocation failed")}
                } };alert.show()
            }.setNegativeButton("Cancel",null).show()
    }
}

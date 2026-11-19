package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** First-class Manpower tab. Staff directory is displayed directly, not behind Salary Planner. */
internal class FinanceManpowerDirectoryScreen(
    private val activity:AppCompatActivity,
    private val onChanged:()->Unit
) {
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density+.5f).toInt()
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun text(value:String,size:Float,bold:Boolean=false,color:Int=0xFF1B2B3D.toInt())=TextView(activity).apply {
        this.text=value;textSize=size;setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun rounded(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(12).toFloat() }
    private fun gap(height:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(height)) }
    private fun palette(name:String):Pair<Int,Int> = when(name.trim().lowercase()) {
        "management","administration" -> 0xFF214A9B.toInt() to 0xFFEAF1FF.toInt()
        "kitchen" -> 0xFF9C510E.toInt() to 0xFFFFF1E1.toInt()
        "housekeeping" -> 0xFF07786C.toInt() to 0xFFE6F7F2.toInt()
        "marketing and sales","marketing","sales" -> 0xFF6F42A3.toInt() to 0xFFF3ECFF.toInt()
        else -> 0xFF43566F.toInt() to 0xFFF0F3F8.toInt()
    }
    private fun groupTitle(name:String,count:Int):View = row().apply {
        val (strong,light)=palette(name)
        setPadding(dp(13),dp(12),dp(13),dp(12));background=rounded(light)
        addView(text(name,15f,true,strong),LinearLayout.LayoutParams(0,-2,1f))
        addView(text("$count staff",11f,true,strong))
    }
    fun render(parent:LinearLayout) {
        parent.removeAllViews()
        val employees=FinancePayrollStore(activity).use { it.employees() }
        parent.addView(row().apply {
            addView(column().apply {
                addView(text("Manpower",23f,true))
                addView(text("Staff · Attendance · Salary",12f,false,0xFF64758A.toInt()))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(text("+ Staff",14f,true,0xFF08764F.toInt()).apply {
                setPadding(dp(14),dp(12),dp(6),dp(12))
                setOnClickListener { FinanceSalaryPlannerScreen(activity,onChanged).showCreateEmployee() }
            })
        })
        parent.addView(gap(11))
        if(employees.isEmpty())parent.addView(text("No staff yet. Tap + Staff to add your first employee.",14f))
        val groups=employees.groupBy {
            when(it.department.trim().lowercase()) {
                "administration"->"Management"
                "marketing","marketing & sales","sales & marketing"->"Marketing and Sales"
                else->it.department
            }
        }
        val order=listOf("Management","Kitchen","Housekeeping","Marketing and Sales")
        groups.keys.sortedWith(compareBy<String> { name ->
            order.indexOf(name).let { if(it<0)Int.MAX_VALUE else it }
        }.thenBy { it.lowercase() }).forEach { dept ->
            val staff=groups[dept].orEmpty().sortedBy { it.name.lowercase() }
            parent.addView(groupTitle(dept,staff.size));parent.addView(gap(7))
            val accent=palette(dept).first
            staff.forEach { employee ->
                parent.addView(row().apply {
                    background=rounded(Color.WHITE)
                    setPadding(dp(12),dp(12),dp(12),dp(12))
                    addView(View(activity).apply { setBackgroundColor(accent) },LinearLayout.LayoutParams(dp(3),dp(35)))
                    addView(column().apply {
                        setPadding(dp(10),0,0,0)
                        addView(text(employee.name,15f,true))
                        addView(text(employee.designation.ifBlank { dept },12f,false,0xFF64758A.toInt()))
                    },LinearLayout.LayoutParams(0,-2,1f))
                    addView(text("Edit",12f,true,accent).apply {
                        setPadding(dp(12),dp(11),dp(10),dp(11))
                        setOnClickListener { FinanceSalaryPlannerScreen(activity,onChanged).showEditEmployee(employee.id) }
                    })
                    addView(text("›",22f,false,0xFF617489.toInt()))
                    setOnClickListener { FinanceSalaryPlannerScreen(activity,onChanged).showForEmployee(employee.id) }
                })
                parent.addView(gap(6))
            }
            parent.addView(gap(8))
        }
        parent.addView(gap(80))
    }
}

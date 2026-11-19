package com.example.bookingregister.finance.dev

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Single navigation home for Finance DEV master-data management. Reuses existing master stores. */
internal class FinanceMastersScreen(
    private val activity:AppCompatActivity,
    private val onChanged:()->Unit
) {
    private var dialog:Dialog?=null
    private val ink=Color.rgb(27,43,61)
    private val muted=Color.rgb(105,116,132)
    private val green=Color.rgb(7,111,75)
    private val background=Color.rgb(247,249,252)
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density+0.5f).toInt()
    private fun col()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun space(value:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(value)) }
    private fun label(value:String,size:Float=14f,bold:Boolean=false,color:Int=ink)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun shape(fill:Int)=GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(14).toFloat();setStroke(dp(1),0xFFE2E8EF.toInt())
    }
    private fun option(name:String,description:String,open:()->Unit):View = col().apply {
        setPadding(dp(15),dp(14),dp(15),dp(14));background=shape(Color.WHITE)
        addView(row().apply {
            addView(col().apply {
                addView(label(name,16f,true))
                addView(space(5))
                addView(label(description,12f,false,muted))
            },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            addView(label("›",28f,false,green))
        })
        isClickable=true;isFocusable=true;contentDescription="Open $name"
        setOnClickListener { open() }
    }
    fun show() {
        dialog=Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar).also {
            it.setOnDismissListener { onChanged() };it.show()
        }
        render()
    }
    private fun render() {
        val d=dialog?:return
        val root=col().apply { setBackgroundColor(this@FinanceMastersScreen.background) }
        root.addView(row().apply {
            setPadding(dp(15),dp(17),dp(15),dp(13));setBackgroundColor(Color.WHITE)
            addView(label("‹",28f,true,green).apply {
                gravity=Gravity.CENTER;setOnClickListener { d.dismiss() }
            },LinearLayout.LayoutParams(dp(47),dp(44)))
            addView(col().apply {
                addView(label("Finance Settings",21f,true))
                addView(label("Masters & configuration",12f,false,muted))
            },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        })
        val body=col().apply { setPadding(dp(15),dp(18),dp(15),dp(30)) }
        body.addView(label("MASTER DATA",12f,true,muted))
        body.addView(space(10))
        body.addView(option("Global Catalog","18 accounting categories · 175 expense types · Item groups") {
            FinanceGlobalCatalogScreen(activity).show { render() }
        })
        body.addView(space(10))
        body.addView(option("Vendor Master","Create or update suppliers, landlords and service providers") {
            val pane=col().apply { setPadding(dp(13),dp(10),dp(13),dp(10)) }
            HotelPurchaseVendorScreen(activity).render(pane)
            AlertDialog.Builder(activity).setTitle("Vendor Master")
                .setView(ScrollView(activity).apply { addView(pane) })
                .setPositiveButton("Close") { _,_->render() }.show()
        })
        body.addView(space(10))
        body.addView(option("Employee Master","Add staff · Department · Monthly salary · Paid-rest policy") {
            FinanceSalaryPlannerScreen(activity,{ render() },masterMode=true).show()
        })
        body.addView(space(20))
        body.addView(label("CONFIGURATION",12f,true,muted))
        body.addView(space(10))
        body.addView(option("Recurring Expenses","Rent, contracts and other monthly obligations") {
            FinanceFixedExpensesScreen(activity) { render() }.show()
        })
        body.addView(space(10))
        body.addView(option("Departments & Properties","Current department list; property settings not yet editable") {
            val departments=FinanceGlobalCatalogDefaults.departments.joinToString("\n") { "• $it" }
            AlertDialog.Builder(activity).setTitle("Departments & Properties")
                .setMessage("Department options currently available:\n\n$departments\n\nProperty master editing is not yet implemented in Finance DEV. Existing records are unchanged.")
                .setPositiveButton("Close",null).show()
        })
        body.addView(space(17))
        body.addView(label("All other screens select existing masters. Bills, payments and vendor ledgers retain their current records and IDs.",12f,false,muted))
        val scroller=ScrollView(activity).apply { addView(body) }
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        d.setContentView(root)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
    }
}

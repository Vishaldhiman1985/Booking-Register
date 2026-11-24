package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

/** One vendor editor under Settings > Masters. Types come from the CA classification catalog. */
class HotelPurchaseVendorScreen(private val activity:Activity) {
    private val store=HotelPurchaseVendorStore(activity)
    private val catalog by lazy { FinanceExpenseCatalogLoader.load(activity) }
    private val configs by lazy { FinanceGlobalCatalogSettings(activity) }
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun col()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun gap(n:Int)=View(activity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(n)) }
    private fun text(s:String,sz:Float=14f,bold:Boolean=false,color:Int=Color.rgb(30,44,64))=TextView(activity).apply {
        text=s;textSize=sz;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun alert(msg:String)=Toast.makeText(activity,msg,Toast.LENGTH_LONG).show()
    private fun shape()=GradientDrawable().apply { setColor(Color.WHITE);cornerRadius=dp(14).toFloat() }
    fun render(container:LinearLayout) {
        container.removeAllViews()
        container.setPadding(dp(12),dp(8),dp(12),dp(18))
        container.addView(text("Vendor Master",21f,true))
        container.addView(gap(8))
        container.addView(text("One vendor record for Purchases, Rent, Expenses and Payables. Archive keeps the ledger history.",12f))
        container.addView(gap(12))
        container.addView(text("+ Add vendor",16f,true,Color.rgb(7,111,75)).apply {
            setPadding(dp(14),dp(12),dp(14),dp(12));background=shape();setOnClickListener { editVendor(container) }
        })
        val all=store.allIncludingArchived().sortedWith(compareBy<HotelPurchaseVendor> { store.isArchived(it.id) }.thenBy { it.name })
        if(all.isEmpty())container.addView(text("No vendors created yet.",13f))
        all.forEach { v ->
            container.addView(gap(9))
            val types=store.typeCodes(v.id)
            val labels=catalog.categories.flatMap { c -> c.types.filter { it.code in types }.map { it.name } }
            container.addView(col().apply {
                background=shape();setPadding(dp(14),dp(13),dp(14),dp(13))
                addView(text(v.name+(if(store.isArchived(v.id))" · ARCHIVED" else ""),16f,true))
                addView(gap(5));addView(text(v.phone.ifBlank { "No phone" },12f))
                addView(text(if(labels.isEmpty())"Legacy vendor · Expense types not assigned" else labels.joinToString(" · "),12f))
                addView(gap(8))
                val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
                row.addView(text("Edit  ›",14f,true,Color.rgb(7,111,75)).apply {
                    setPadding(dp(8),dp(6),dp(20),dp(6));setOnClickListener { editVendor(container,v) }
                },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                row.addView(text(if(store.isArchived(v.id))"Restore  ›" else "Archive  ›",13f,true,Color.rgb(160,79,35)).apply {
                    setPadding(dp(8),dp(6),dp(8),dp(6));setOnClickListener { archiveVendor(container,v) }
                })
                addView(row)
            })
        }
    }
    private fun archiveVendor(parent:LinearLayout,v:HotelPurchaseVendor) {
        val archived=store.isArchived(v.id)
        if(!archived) {
            val activeContracts=FinanceFixedExpenseStore(activity).use { fixed ->
                fixed.rules().count { it.vendorId==v.id && it.active }
            }
            if(activeContracts>0) {
                AlertDialog.Builder(activity).setTitle("Active recurring agreements")
                    .setMessage("This vendor has $activeContracts active recurring agreement(s). Stop those schedules in Settings > Recurring Expenses before archiving this vendor.")
                    .setPositiveButton("Close",null).show()
                return
            }
        }
        val reason=EditText(activity).apply { hint="Mandatory reason for ${if(archived)"restoring" else "archiving"}" }
        AlertDialog.Builder(activity).setTitle(if(archived)"Restore ${v.name}" else "Archive ${v.name}?")
            .setMessage("Existing bills and payments are preserved. Archiving prevents use in new transactions; it does not cancel recurring contracts.")
            .setView(reason).setNegativeButton("Cancel",null)
            .setPositiveButton(if(archived)"Restore" else "Archive") { _,_ ->
                try { store.setArchived(v.id,!archived,reason.text.toString());render(parent) }
                catch(e:Exception){ alert(e.message?:"Unable to update vendor status") }
            }.show()
    }
    /** Same vendor master editor reused from the new Vendors tab, never a second vendor database. */
    fun showCreate(onSaved: () -> Unit) { editVendor(col(), null, onSaved) }
    fun showEdit(id: String, onSaved: () -> Unit) {
        val existing = store.allIncludingArchived().firstOrNull { it.id == id }
            ?: error("Vendor not found in the master; historical payees are read-only")
        editVendor(col(), existing, onSaved)
    }
    private fun editVendor(parent:LinearLayout,existing:HotelPurchaseVendor?=null,
                           onSaved: () -> Unit = { render(parent) }) {
        val body=col().apply { setPadding(dp(16),dp(12),dp(16),dp(20)) }
        val name=EditText(activity).apply { hint="Vendor / landlord legal name";setText(existing?.name.orEmpty()) }
        val phone=EditText(activity).apply { hint="Phone (optional)";inputType=InputType.TYPE_CLASS_PHONE;setText(existing?.phone.orEmpty()) }
        body.addView(text("VENDOR NAME",12f,true));body.addView(name)
        body.addView(text("CONTACT NUMBER",12f,true));body.addView(phone)
        body.addView(gap(12))
        body.addView(text("SERVICES / EXPENSE CLASSIFICATIONS",13f,true))
        body.addView(text("Select multiple categories, then tick the expense types this vendor normally provides.",12f))
        body.addView(gap(8))
        val selected=store.typeCodes(existing?.id.orEmpty()).toMutableSet()
        catalog.categories.forEach { category ->
            val selectedHere=category.types.any { it.code in selected }
            val toggle=CheckBox(activity).apply {
                text=category.name;isChecked=selectedHere;textSize=13f
            }
            body.addView(toggle)
            val section=col().apply { setPadding(dp(20),0,0,dp(5));visibility=if(selectedHere)View.VISIBLE else View.GONE }
            val boxes=category.types.map { t ->
                CheckBox(activity).apply {
                    text=t.name;isChecked=t.code in selected;textSize=12.5f
                    setOnCheckedChangeListener { _,checked -> if(checked)selected.add(t.code) else selected.remove(t.code) }
                    section.addView(this)
                }
            }
            toggle.setOnCheckedChangeListener { _,checked ->
                section.visibility=if(checked)View.VISIBLE else View.GONE
                if(!checked)boxes.forEach { it.isChecked=false }
            }
            body.addView(section)
        }
        val reason=EditText(activity).apply { hint="Reason for updating vendor" }
        if(existing!=null){body.addView(gap(8));body.addView(text("CHANGE REASON",12f,true));body.addView(reason)}
        body.addView(gap(8))
        body.addView(text("Existing purchasing item groups are preserved. Catalog-linked groups are added automatically for item-based expense types.",11f))
        val dialog=AlertDialog.Builder(activity).setTitle(if(existing==null)"New Vendor" else "Edit Vendor")
            .setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val vName=name.text.toString().trim()
                    require(vName.isNotBlank()) { "Vendor name is required" }
                    require(selected.isNotEmpty()) { "Choose at least one expense type" }
                    val existingCodes=catalog.categories.flatMap { it.types }.map { it.code }.toSet()
                    require(existingCodes.containsAll(selected)) { "An expense type is no longer present in Global Catalog" }
                    val purchaseGroups=selected.flatMap { configs.forType(it).groupIds }.toSet()
                    val v=HotelPurchaseVendor(
                        id=existing?.id?:UUID.randomUUID().toString(),name=vName,
                        phone=phone.text.toString().trim(),
                        suppliedCategoryIds=existing?.suppliedCategoryIds.orEmpty()+purchaseGroups
                    )
                    store.saveWithTypes(v,selected,reason.text.toString())
                    dialog.dismiss();onSaved()
                }catch(e:Exception){alert(e.message?:"Unable to save vendor")}
            }
        }
        dialog.show()
    }
}

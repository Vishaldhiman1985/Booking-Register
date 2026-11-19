package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** One vendor ID and one master. Legacy purchasing category IDs remain intact. */
class HotelPurchaseVendorStore(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_purchase_vendors_v1", Context.MODE_PRIVATE)

    private fun vendorArray(): JSONArray = JSONArray(prefs.getString("vendors", "[]") ?: "[]")
    fun allIncludingArchived(): List<HotelPurchaseVendor> {
        val array = vendorArray()
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val categories = obj.optJSONArray("categories") ?: JSONArray()
            HotelPurchaseVendor(
                id = obj.getString("id"), name = obj.getString("name"), phone = obj.optString("phone"),
                suppliedCategoryIds = (0 until categories.length()).map { categories.getString(it) }.toSet()
            )
        }
    }
    /** Archived suppliers are removed from NEW transaction pickers, not from historic records. */
    fun all(): List<HotelPurchaseVendor> = allIncludingArchived().filterNot { isArchived(it.id) }
    fun isArchived(id:String): Boolean = prefs.getBoolean("archived_$id",false)
    fun typeCodes(id:String): Set<String> {
        val a=JSONArray(prefs.getString("types_$id","[]") ?: "[]")
        return (0 until a.length()).map { a.getString(it) }.toSet()
    }
    private fun toVendorJson(list:List<HotelPurchaseVendor>) = JSONArray().apply {
        list.forEach { v -> put(JSONObject().apply {
            put("id",v.id);put("name",v.name);put("phone",v.phone)
            put("categories",JSONArray(v.suppliedCategoryIds.sorted()))
        }) }
    }
    private fun auditEntry(id:String,operation:String,reason:String,summary:String):String {
        val a=JSONArray(prefs.getString("master_audit_v2","[]") ?: "[]")
        a.put(JSONObject().apply {
            put("id",id);put("operation",operation);put("reason",reason)
            put("summary",summary);put("date",LocalDateTime.now().toString())
        })
        return a.toString()
    }
    /** Compatibility for older purchasing flows; never alters existing type-code links. */
    fun save(vendor:HotelPurchaseVendor) {
        require(vendor.id.isNotBlank() && vendor.name.isNotBlank())
        val records=allIncludingArchived().filterNot { it.id==vendor.id }+vendor
        check(prefs.edit().putString("vendors",toVendorJson(records).toString()).commit()) {
            "Could not persist vendor"
        }
    }
    /** Atomic save of vendor identity + selected accounting expense types. */
    fun saveWithTypes(vendor:HotelPurchaseVendor,typeCodes:Set<String>,reason:String) {
        require(vendor.id.isNotBlank() && vendor.name.isNotBlank())
        require(typeCodes.isNotEmpty()) { "Select at least one expense type" }
        val prior=allIncludingArchived().firstOrNull { it.id==vendor.id }
        if(prior!=null)require(reason.trim().length>=5) { "Enter reason for vendor changes (5+ characters)" }
        val list=allIncludingArchived().filterNot { it.id==vendor.id }+vendor
        val previousTypes=this.typeCodes(vendor.id)
        val log=auditEntry(vendor.id,if(prior==null)"CREATE" else "EDIT",reason.trim(),
            "${prior?.name ?: "New vendor"} -> ${vendor.name}; types $previousTypes -> $typeCodes")
        check(prefs.edit()
            .putString("vendors",toVendorJson(list).toString())
            .putString("types_${vendor.id}",JSONArray(typeCodes.sorted()).toString())
            .putString("master_audit_v2",log)
            .commit()) { "Could not save vendor and accounting links" }
    }
    /** Archive rather than deleting transaction-linked vendor identity. */
    fun setArchived(id:String,archived:Boolean,reason:String) {
        require(reason.trim().length>=5) { "Mandatory reason (5+ characters)" }
        val vendor=allIncludingArchived().firstOrNull { it.id==id } ?: error("Vendor not found")
        val log=auditEntry(id,if(archived)"ARCHIVE" else "RESTORE",reason.trim(),vendor.name)
        check(prefs.edit().putBoolean("archived_$id",archived).putString("master_audit_v2",log).commit()) {
            "Vendor status not saved"
        }
    }
}

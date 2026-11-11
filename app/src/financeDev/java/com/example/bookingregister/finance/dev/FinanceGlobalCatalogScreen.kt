package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Finance DEV's single catalog management surface.
 * CA hierarchy -> accounting expense type -> operational item group -> physical item.
 * Purchase Orders and Monthly Stock continue using the *existing* item/category stores.
 * No production database, receipt, accounting or stock history is modified here.
 */
internal class FinanceGlobalCatalogScreen(private val activity: Activity) {
    private val caMaster by lazy { FinanceExpenseCatalogLoader.load(activity) }
    private val settings by lazy { FinanceGlobalCatalogSettings(activity) }
    private val groups by lazy { HotelPurchaseCategoryStore(activity) }
    private val items by lazy { HotelPurchaseCustomItemStore(activity) }

    private var selectedCategory: SmartExpenseCategory? = null
    private var selectedType: SmartExpenseType? = null
    private var selectedGroup: HotelPurchaseCategory? = null
    private var allGroups = false
    private var showArchivedItems = false
    private var dialog: Dialog? = null

    private val bg = Color.rgb(247, 249, 252)
    private val white = Color.WHITE
    private val navy = Color.rgb(28, 42, 62)
    private val muted = Color.rgb(104, 118, 136)
    private val border = Color.rgb(225, 232, 240)
    private val emerald = Color.rgb(4, 112, 81)
    private val pale = Color.rgb(236, 249, 243)
    private val blue = Color.rgb(42, 97, 189)
    private val bluePale = Color.rgb(236, 244, 255)
    private val dp: (Int) -> Int = { v -> (v * activity.resources.displayMetrics.density + .5f).toInt() }

    private fun rounded(fill: Int, outline: Int? = null, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (outline != null) setStroke(dp(1), outline)
        }

    private fun text(value: String, size: Float = 14f, strong: Boolean = false, color: Int = navy): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
            includeFontPadding = true
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun column(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun gap(height: Int = 10) = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun action(label: String, primary: Boolean = false, click: () -> Unit): TextView =
        text(label, 13f, true, if (primary) white else emerald).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(46)
            background = rounded(if (primary) emerald else pale, null, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun hint(value: String, tint: Int = pale, ink: Int = emerald): TextView =
        text(value, 12f, true, ink).apply {
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = rounded(tint, null, 9)
        }

    private fun card(title: String, subtitle: String, meta: String = "", click: () -> Unit): View =
        row().apply {
            background = rounded(white, border, 15)
            setPadding(dp(14), dp(13), dp(13), dp(13))
            minimumHeight = dp(76)
            val content = column().apply {
                addView(text(title, 15f, true).apply { maxLines = 2 })
                addView(gap(4))
                addView(text(subtitle, 12f, false, muted).apply { maxLines = 2 })
            }
            addView(content, LinearLayout.LayoutParams(0, -2, 1f))
            if (meta.isNotBlank()) {
                val metaText = text(meta, 11f, true, emerald).apply {
                    background = rounded(pale, null, 8)
                    setPadding(dp(7), dp(5), dp(7), dp(5))
                    maxLines = 2
                    gravity = Gravity.CENTER
                }
                addView(metaText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            }
            addView(text("›", 25f, false, muted), LinearLayout.LayoutParams(dp(20), -2).apply { leftMargin = dp(8) })
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun currentTitle(): String = when {
        selectedGroup != null -> groups.allIncludingArchived().firstOrNull { it.first.id == selectedGroup!!.id }?.first?.name
            ?: selectedGroup!!.name
        allGroups -> "Item groups"
        selectedType != null -> selectedType!!.name
        selectedCategory != null -> selectedCategory!!.name
        else -> "Global Catalog"
    }

    private fun currentSubtitle(): String = when {
        selectedGroup != null -> "Shared physical items · purchase unit · stock unit"
        allGroups -> "Operational group names are shared by Purchase and Stock"
        selectedType != null -> "Expense type ${selectedType!!.code} · item groups and stock rules"
        selectedCategory != null -> "CA category ${selectedCategory!!.code} · accounting expense types"
        else -> "One organized master for Expenses, Purchase Orders and Stock"
    }

    fun show(onChanged: () -> Unit = {}) {
        val d = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        dialog = d
        d.setOnDismissListener { onChanged() }
        render()
        d.show()
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            statusBarColor = bg
            navigationBarColor = white
        }
    }

    private fun goBack() {
        when {
            selectedGroup != null -> selectedGroup = null
            allGroups -> allGroups = false
            selectedType != null -> selectedType = null
            selectedCategory != null -> selectedCategory = null
            else -> { dialog?.dismiss(); return }
        }
        showArchivedItems = false
        render()
    }

    private fun render() {
        val d = dialog ?: return
        val screen = column().apply { setBackgroundColor(bg) }
        val top = row().apply {
            setPadding(dp(14), dp(13), dp(14), dp(12))
            setBackgroundColor(white)
        }
        val back = text("‹", 33f, false, navy).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { goBack() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        top.addView(column().apply {
            addView(text("BOOKING REGISTER", 11f, true, muted))
            addView(text("Catalog Management", 18f, true, navy))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("×", 28f, false, navy).apply {
            gravity = Gravity.CENTER
            contentDescription = "Close catalog"
            setOnClickListener { d.dismiss() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        screen.addView(top)

        val heading = column().apply {
            setPadding(dp(20), dp(17), dp(20), dp(10))
            addView(text(currentTitle(), 24f, true))
            addView(gap(4))
            addView(text(currentSubtitle(), 13f, false, muted))
            addView(gap(11))
            addView(text(breadcrumb(), 11f, true, blue))
        }
        screen.addView(heading)

        val tools = row().apply { setPadding(dp(18), dp(2), dp(18), dp(10)) }
        val search = EditText(activity).apply {
            isSingleLine = true
            textSize = 14f
            hint = when {
                selectedGroup != null -> "Search items or aliases"
                allGroups -> "Search groups"
                selectedType != null -> "Search linked item groups"
                selectedCategory != null -> "Search expense types"
                else -> "Search CA categories"
            }
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(white, border, 12)
            minHeight = dp(49)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        tools.addView(search, LinearLayout.LayoutParams(0, dp(49), 1f))
        screen.addView(tools)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val list = column().apply { setPadding(dp(18), dp(4), dp(18), dp(24)) }
        scroll.addView(list)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = row().apply {
            setPadding(dp(18), dp(10), dp(18), dp(13))
            setBackgroundColor(white)
        }
        when {
            selectedGroup != null -> {
                bottom.addView(action(if (showArchivedItems) "Hide archived" else "Show archived") {
                    showArchivedItems = !showArchivedItems
                    render()
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                bottom.addView(View(activity), LinearLayout.LayoutParams(dp(10), 1))
                bottom.addView(action("+ Add item", true) { editItem(null) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            allGroups -> bottom.addView(action("+ Add item group", true) { addGroup(null) }, LinearLayout.LayoutParams(-1, dp(48)))
            selectedType != null -> {
                bottom.addView(action("Edit stock rules", false) { editTypeSettings() }, LinearLayout.LayoutParams(0, dp(48), 1f))
                bottom.addView(View(activity), LinearLayout.LayoutParams(dp(10), 1))
                bottom.addView(action("+ Item group", true) { addGroup(selectedType!!.code) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            else -> bottom.addView(action("Manage operational item groups") {
                allGroups = true
                selectedCategory = null
                selectedType = null
                selectedGroup = null
                render()
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        screen.addView(bottom)
        d.setContentView(screen)

        fun updateList(value: String) {
            list.removeAllViews()
            val query = value.trim()
            when {
                selectedGroup != null -> renderItems(list, query)
                allGroups -> renderAllGroups(list, query)
                selectedType != null -> renderGroups(list, query)
                selectedCategory != null -> renderTypes(list, query)
                else -> renderCategories(list, query)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateList(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateList("")
    }

    private fun breadcrumb(): String = when {
        selectedGroup != null -> listOfNotNull("All categories", selectedCategory?.name, selectedType?.name, currentTitle()).joinToString("  ›  ")
        allGroups -> "All categories  ›  Operational item groups"
        selectedType != null -> "All categories  ›  ${selectedCategory!!.name}  ›  ${selectedType!!.name}"
        selectedCategory != null -> "All categories  ›  ${selectedCategory!!.name}"
        else -> "18 accounting categories  ·  175 expense types"
    }

    private fun section(parent: LinearLayout, title: String, supporting: String = "") {
        parent.addView(text(title, 13f, true, navy).apply { setPadding(dp(1), dp(3), 0, dp(6)) })
        if (supporting.isNotBlank()) parent.addView(text(supporting, 12f, false, muted).apply {
            setPadding(dp(1), 0, 0, dp(7))
        })
        parent.addView(gap(5))
    }

    private fun put(parent: LinearLayout, view: View) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2))
        parent.addView(gap(9))
    }

    private fun empty(parent: LinearLayout, title: String, description: String) {
        put(parent, column().apply {
            setPadding(dp(18), dp(24), dp(18), dp(24))
            background = rounded(white, border, 16)
            addView(text(title, 16f, true))
            addView(gap(7))
            addView(text(description, 13f, false, muted))
        })
    }

    private fun renderCategories(parent: LinearLayout, query: String) {
        section(parent, "ACCOUNTING CATEGORIES", "These are your CA-oriented categories. Codes remain stable.")
        val categories = caMaster.categories.filter { it.name.contains(query, true) || it.code.contains(query, true) }
        if (categories.isEmpty()) empty(parent, "No categories found", "Try a different name or code.")
        categories.forEach { category ->
            val active = category.types.count { settings.forType(it.code).stockable }
            put(parent, card(category.name, "${category.code}  ·  ${category.types.size} expense types", if (active > 0) "$active stock" else "") {
                selectedCategory = category; render()
            })
        }
    }

    private fun renderTypes(parent: LinearLayout, query: String) {
        val cat = selectedCategory ?: return
        section(parent, "EXPENSE TYPES", "Select a type to see its department, stock rules and item groups.")
        val types = cat.types.filter { it.name.contains(query, true) || it.code.contains(query, true) }
        if (types.isEmpty()) empty(parent, "No expense types found", "Try another keyword.")
        types.forEach { type ->
            val config = settings.forType(type.code)
            val state = if (config.stockable) "Stock on" else "Expense"
            put(parent, card(type.name, "${type.code}  ·  ${config.department}  ·  ${config.groupIds.size} linked groups", state) {
                selectedType = type; render()
            })
        }
    }

    private fun renderGroups(parent: LinearLayout, query: String) {
        val type = selectedType ?: return
        val config = settings.forType(type.code)
        val summary = column().apply {
            background = rounded(if (config.stockable) pale else bluePale, null, 15)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            addView(text(if (config.stockable) "STOCK TRACKING ENABLED" else "ACCOUNTING ONLY · STOCK OFF", 12f, true,
                if (config.stockable) emerald else blue))
            addView(gap(5))
            addView(text("Default department: ${config.department}", 14f, true))
            addView(gap(4))
            addView(text("The chosen department can be overridden on a transaction. Linked groups organize items; they do not create duplicate items.", 12f, false, muted))
        }
        put(parent, summary)
        section(parent, "OPERATIONAL ITEM GROUPS", "These groups are shared by Purchase, Direct Expenses and Stock.")
        val linked = groups.all().filter { it.id in config.groupIds && it.name.contains(query, true) }
        if (linked.isEmpty()) empty(parent, "No linked groups", "Tap Edit stock rules to select existing groups, or create a new group.")
        linked.forEach { group ->
            val count = items.catalogItems().count { it.categoryId == group.id }
            put(parent, card(group.name, "${count} active items  ·  Group ID: ${group.id}", "$count items") {
                selectedGroup = group; render()
            })
        }
        if (!config.stockable) {
            parent.addView(text("Inventory movements remain disabled for this expense type until Stock Tracking is enabled. This catalog screen does not post stock.", 12f, false, muted))
        }
    }

    private fun renderAllGroups(parent: LinearLayout, query: String) {
        section(parent, "SHARED OPERATIONAL GROUPS", "Manage group names here. Their item IDs and purchase history remain intact.")
        val filtered = groups.allIncludingArchived().filter { it.first.name.contains(query, true) }
        if (filtered.isEmpty()) empty(parent, "No matching groups", "Create a new group or try another search.")
        filtered.forEach { (group, active) ->
            val container = row()
            container.addView(card(group.name, "${items.catalogItems(true).count { it.categoryId == group.id }} catalog items", if (active) "Active" else "Archived") {
                selectedGroup = group; render()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(text("⋮", 25f, false, navy).apply {
                gravity = Gravity.CENTER
                contentDescription = "Manage ${group.name}"
                setOnClickListener { anchor -> groupMenu(group, active, anchor) }
            }, LinearLayout.LayoutParams(dp(42), dp(64)))
            put(parent, container)
        }
    }

    private fun renderItems(parent: LinearLayout, query: String) {
        val groupId = selectedGroup?.id ?: return
        val all = items.catalogItems(true).filter { it.categoryId == groupId }
        val filtered = all.filter { (showArchivedItems || it.active) &&
            (query.isEmpty() || it.name.contains(query, true) || it.synonyms.any { alias -> alias.contains(query, true) })
        }.sortedWith(compareByDescending<HotelPurchaseItem> { it.active }.thenBy { it.name.lowercase() })
        section(parent, "PHYSICAL ITEM CATALOG", "${all.count { it.active }} active  ·  ${all.count { !it.active }} archived  ·  Shared item IDs")
        if (filtered.isEmpty()) empty(parent, "No matching items", "Use Add item for missing products. Never duplicate an item that already exists.")
        filtered.forEach { item ->
            val container = row()
            container.addView(card(item.name, "Stock unit: ${item.unit}  ·  ${if (item.active) "Active" else "Archived"}", item.unit) {
                editItem(item)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(text("⋮", 25f, false, navy).apply {
                gravity = Gravity.CENTER
                contentDescription = "Actions for ${item.name}"
                setOnClickListener { anchor -> itemMenu(item, anchor) }
            }, LinearLayout.LayoutParams(dp(40), dp(66)))
            put(parent, container)
        }
        parent.addView(text("Existing item IDs are preserved. If an item has order or stock history, its name/unit cannot be silently changed.", 12f, false, muted))
    }

    private fun editTypeSettings() {
        val type = selectedType ?: return
        val config = settings.forType(type.code)
        val all = groups.all()
        val form = column().apply { setPadding(dp(18), dp(12), dp(18), dp(14)) }
        form.addView(text(type.name, 17f, true))
        form.addView(gap(5))
        form.addView(text("Accounting code ${type.code} is fixed. Configure how this type appears in operations.", 12f, false, muted))
        form.addView(gap(15))
        val stock = CheckBox(activity).apply {
            text = "Track physical stock for this type"
            isChecked = config.stockable
            textSize = 14f
        }
        form.addView(stock)
        form.addView(gap(8))
        form.addView(text("DEFAULT DEPARTMENT", 12f, true, muted))
        val dept = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, FinanceGlobalCatalogDefaults.departments)
            setSelection(FinanceGlobalCatalogDefaults.departments.indexOf(config.department).coerceAtLeast(0))
            minimumHeight = dp(45)
        }
        form.addView(dept)
        form.addView(gap(16))
        form.addView(text("LINKED ITEM GROUPS", 12f, true, muted))
        form.addView(text("Choose all groups relevant to this expense type. A group can be linked to more than one expense type.", 12f, false, muted))
        form.addView(gap(9))
        val checks = all.map { group ->
            val field = CheckBox(activity).apply {
                text = group.name
                textSize = 14f
                isChecked = group.id in config.groupIds
                setPadding(dp(1), dp(3), 0, dp(3))
            }
            form.addView(field)
            group.id to field
        }
        form.addView(gap(13))
        form.addView(text("REASON FOR CHANGE", 12f, true, muted))
        val editReason = EditText(activity).apply {
            hint = "Why are these settings changing?"
            setSingleLine(true)
        }
        form.addView(editReason)
        val scroller = ScrollView(activity).apply { addView(form) }
        val editor = AlertDialog.Builder(activity)
            .setTitle("Expense type settings")
            .setView(scroller)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val selected = checks.filter { it.second.isChecked }.map { it.first }.toSet()
                    val changed = stock.isChecked != config.stockable ||
                        dept.selectedItem.toString() != config.department || selected != config.groupIds
                    if (changed) {
                        val note = editReason.text.toString().trim()
                        require(note.length >= 3) { "Enter a reason for changing the type settings" }
                        settings.save(type.code, stock.isChecked, dept.selectedItem.toString(), selected)
                        HotelCatalogChangeLog(activity).record("TYPE_CONFIG_EDIT", type.code, note)
                    }
                    editor.dismiss()
                    render()
                    toast("Expense type settings saved")
                } catch (e: Exception) { toast(e.message ?: "Unable to save settings") }
            }
        }
        editor.show()
    }

    private fun addGroup(linkType: String?) {
        val field = EditText(activity).apply {
            hint = "Item group name"
            setSingleLine(true)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val editor = AlertDialog.Builder(activity)
            .setTitle("New operational item group")
            .setView(field)
            .setMessage("New groups are shared by all Finance modules.")
            .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create()
        editor.setOnShowListener {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val created = groups.add(field.text.toString())
                    if (linkType != null) {
                        val before = settings.forType(linkType)
                        settings.save(linkType, before.stockable, before.department, before.groupIds + created.id)
                    }
                    HotelCatalogChangeLog(activity).record("GROUP_ADD", created.id, "Created in global catalog")
                    editor.dismiss(); render()
                } catch (e: Exception) { field.error = e.message ?: "Could not create group" }
            }
        }
        editor.show()
    }

    private fun groupMenu(group: HotelPurchaseCategory, active: Boolean, anchor: View) {
        PopupMenu(activity, anchor).apply {
            if (active) { menu.add("Rename"); menu.add("Archive") } else menu.add("Restore")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Rename" -> renameGroup(group)
                    "Archive" -> reasonDialog("Archive group", "Reason for archiving") { reason ->
                        groups.archive(group.id)
                        HotelCatalogChangeLog(activity).record("GROUP_ARCHIVE", group.id, reason)
                        render()
                    }
                    "Restore" -> reasonDialog("Restore group", "Reason for restoring") { reason ->
                        groups.restore(group.id)
                        HotelCatalogChangeLog(activity).record("GROUP_RESTORE", group.id, reason)
                        render()
                    }
                }
                true
            }
            show()
        }
    }

    private fun renameGroup(group: HotelPurchaseCategory) {
        val name = EditText(activity).apply {
            setText(group.name)
            setSingleLine(true)
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        val reason = EditText(activity).apply {
            hint = "Reason for renaming (required)"
            setSingleLine(true)
        }
        val container = column().apply {
            setPadding(dp(15), dp(8), dp(15), dp(5))
            addView(name)
            addView(gap(9))
            addView(reason)
            addView(gap(9))
            addView(text("A rename does not change item group IDs or historical transactions.", 12f, false, muted))
        }
        val dlg = AlertDialog.Builder(activity).setTitle("Rename item group")
            .setView(container).setNegativeButton("Cancel", null).setPositiveButton("Rename", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                tryAction {
                    val note = reason.text.toString().trim()
                    require(note.length >= 3) { "A reason for renaming is required" }
                    groups.rename(group.id, name.text.toString())
                    HotelCatalogChangeLog(activity).record("GROUP_RENAME", group.id, note)
                    dlg.dismiss(); render()
                }
            }
        }
        dlg.show()
    }

    private fun itemMenu(item: HotelPurchaseItem, anchor: View) {
        PopupMenu(activity, anchor).apply {
            menu.add("Edit")
            menu.add(if (item.active) "Archive" else "Restore")
            setOnMenuItemClickListener { choice ->
                when (choice.title.toString()) {
                    "Edit" -> editItem(item)
                    "Archive" -> reasonDialog("Archive item", "Reason for archiving") { reason ->
                        items.archive(item.id)
                        HotelCatalogChangeLog(activity).record("ITEM_ARCHIVE", item.id, reason)
                        render()
                    }
                    "Restore" -> reasonDialog("Restore item", "Reason for restoring") { reason ->
                        items.restore(item.id)
                        HotelCatalogChangeLog(activity).record("ITEM_RESTORE", item.id, reason)
                        render()
                    }
                }
                true
            }
            show()
        }
    }

    private val unitOptions = listOf("kg", "gram", "litre", "ml", "piece", "packet", "box", "bottle", "dozen", "bag", "roll", "set", "pair", "carton", "service")

    private fun editItem(existing: HotelPurchaseItem?) {
        val availableGroups = groups.all()
        if (availableGroups.isEmpty()) { toast("Add an item group first"); return }
        val initialGroupId = existing?.categoryId ?: selectedGroup?.id
        val form = column().apply { setPadding(dp(17), dp(7), dp(17), dp(9)) }
        fun field(title: String, input: View) {
            form.addView(text(title, 12f, true, muted))
            form.addView(gap(4))
            form.addView(input)
            form.addView(gap(12))
        }
        val name = EditText(activity).apply {
            setSingleLine(true)
            hint = "e.g. Basmati Rice"
            setText(existing?.name.orEmpty())
            textSize = 15f
        }
        field("ITEM NAME", name)
        val group = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, availableGroups.map { it.name })
            setSelection(availableGroups.indexOfFirst { it.id == initialGroupId }.coerceAtLeast(0))
        }
        field("ITEM GROUP", group)
        val units = (unitOptions + listOfNotNull(existing?.unit)).distinct()
        val unit = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, units)
            setSelection(units.indexOf(existing?.unit ?: "piece").coerceAtLeast(0))
        }
        field("STOCK UNIT", unit)
        val aliases = EditText(activity).apply {
            hint = "Hindi / alternative names, separated by commas"
            setText(existing?.synonyms?.joinToString(", ").orEmpty())
            minLines = 2
            maxLines = 3
        }
        field("ALTERNATIVE NAMES (OPTIONAL)", aliases)
        val reason = EditText(activity).apply {
            hint = "Why is this item being changed?"
            minLines = 1
            maxLines = 2
        }
        if (existing != null) {
            form.addView(text("Existing item ID: ${existing.id}", 11f, false, muted))
            form.addView(gap(7))
            field("REASON FOR EDIT (REQUIRED)", reason)
            form.addView(text("Once used in orders, receipts or stock, name and unit are protected.", 12f, false, muted))
        }
        val dlg = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Add catalog item" else "Edit catalog item")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val categoryId = availableGroups[group.selectedItemPosition].id
                    val unitName = unit.selectedItem.toString()
                    val synonyms = aliases.text.toString().split(',').map(String::trim).filter(String::isNotEmpty)
                    if (existing == null) {
                        val created = items.add(name.text.toString(), categoryId, unitName, synonyms)
                        HotelCatalogChangeLog(activity).record("ITEM_ADD", created.id, "Created in Global Catalog")
                    } else {
                        val note = reason.text.toString().trim()
                        require(note.length >= 3) { "Enter a reason for editing the catalog item" }
                        items.edit(existing.id, name.text.toString(), categoryId, unitName, synonyms)
                        HotelCatalogChangeLog(activity).record("ITEM_EDIT", existing.id, note)
                    }
                    dlg.dismiss(); render(); toast("Catalog item saved")
                } catch (e: Exception) { toast(e.message ?: "Unable to save catalog item") }
            }
        }
        dlg.show()
    }

    private fun reasonDialog(title: String, message: String, operation: (String) -> Unit) {
        val reason = EditText(activity).apply {
            hint = message
            setSingleLine(true)
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        val dlg = AlertDialog.Builder(activity).setTitle(title)
            .setView(reason).setNegativeButton("Cancel", null).setPositiveButton("Confirm", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val note = reason.text.toString().trim()
                if (note.length < 3) { reason.error = "A reason is required"; return@setOnClickListener }
                tryAction { operation(note); dlg.dismiss() }
            }
        }
        dlg.show()
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun tryAction(action: () -> Unit) {
        try { action() } catch (e: Exception) { toast(e.message ?: "Catalog action failed") }
    }
}

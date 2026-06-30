package com.example.bookingregister.ui.food

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookingregister.R
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.repository.FoodBillingRepository
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.RoomEntity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class TakeFoodOrderFragment : Fragment(R.layout.activity_take_food_order) {

    private lateinit var repository: FoodBillingRepository

    private val menuItems = mutableListOf<FoodMenuItemEntity>()
    private val filteredMenuItems = mutableListOf<FoodMenuItemEntity>()
    private val rooms = mutableListOf<RoomEntity>()
    private val checkedInBookings = mutableListOf<BookingEntity>()
    private val orderTargets = mutableListOf<FoodOrderTarget>()
    private val quantities = linkedMapOf<String, Int>()

    private lateinit var roomSpinner: Spinner
    private lateinit var guestInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var cartItemsText: TextView
    private lateinit var cartAmountText: TextView
    private lateinit var previewButton: Button
    private lateinit var adapter: FoodMenuAdapter

    private var selectedCategory = "All"
    private val moneyFormat = DecimalFormat("0.##")

    private data class FoodOrderTarget(
        val label: String,
        val booking: BookingEntity?,
        val room: RoomEntity?
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hotelRemoteId =
            arguments?.getString(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID)
                ?: requireActivity().intent.getStringExtra(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID)
                ?: ""

        if (hotelRemoteId.isBlank()) {
            Toast.makeText(requireContext(), "Hotel access missing", Toast.LENGTH_LONG).show()
            return
        }

        repository = FoodBillingRepository(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            hotelRemoteId = hotelRemoteId
        )

        roomSpinner = view.findViewById(R.id.spRoom)
        guestInput = view.findViewById(R.id.etGuestName)
        searchInput = view.findViewById(R.id.etSearch)
        chipGroup = view.findViewById(R.id.chipGroupCategories)
        recyclerView = view.findViewById(R.id.rvMenu)
        cartItemsText = view.findViewById(R.id.tvCartItems)
        cartAmountText = view.findViewById(R.id.tvCartAmount)
        previewButton = view.findViewById(R.id.btnPreviewOrder)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FoodMenuAdapter(
            items = filteredMenuItems,
            quantities = quantities,
            onQuantityChanged = { updateCart() }
        )

        recyclerView.adapter = adapter

        searchInput.addTextChangedListener(SimpleTextWatcher {
            applyFilters()
        })

        previewButton.setOnClickListener {
            val draftItems = buildDraftItems()
            if (draftItems.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least one item", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPreview(draftItems)
        }

        observeData()
        updateCart()
    }

    private fun observeData() {
        repository.observeRooms().observe(viewLifecycleOwner) { list ->
            rooms.clear()
            rooms.addAll(list.filter { !it.isDeleted })
            setupRoomSpinner()
        }

        repository.observeBookings().observe(viewLifecycleOwner) { list ->
            checkedInBookings.clear()
            checkedInBookings.addAll(
                list.filter { !it.isDeleted && it.bookingStatus == BookingStatus.CHECKED_IN }
                    .sortedWith(compareBy<BookingEntity> { it.checkOutMillis }.thenBy { it.guestName.lowercase() })
            )
            setupRoomSpinner()
        }

        repository.observeFoodMenuItems().observe(viewLifecycleOwner) { list ->
            val activeItems = list.filter { !it.isDeleted && it.isActive }
                .sortedBy { it.itemName.lowercase() }

            menuItems.clear()
            menuItems.addAll(activeItems)

            setupCategoryChips()
            applyFilters()

            if (menuItems.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No active menu items found for this hotel.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRoomSpinner() {
        val previouslySelectedLabel = roomSpinner.selectedItem?.toString()
        val roomById = rooms.associateBy { it.remoteId }

        orderTargets.clear()
        orderTargets.add(FoodOrderTarget(label = "Table Sale", booking = null, room = null))

        checkedInBookings.forEach { booking ->
            booking.roomRemoteIds
                .mapNotNull { roomById[it] }
                .sortedBy { it.roomName.lowercase() }
                .forEach { room ->
                    val guestLabel = booking.guestName.takeIf { it.isNotBlank() } ?: "Checked-in guest"
                    orderTargets.add(
                        FoodOrderTarget(
                            label = "${room.roomName} - $guestLabel",
                            booking = booking,
                            room = room
                        )
                    )
                }
        }

        val labels = orderTargets.map { it.label }

        roomSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val selectedIndex = labels.indexOf(previouslySelectedLabel).takeIf { it >= 0 } ?: 0
        roomSpinner.setSelection(selectedIndex)
    }

    private fun setupCategoryChips() {
        chipGroup.removeAllViews()

        val categories = mutableListOf("All")
        categories.addAll(
            menuItems.map { it.categoryName.orEmpty().trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        )

        if (selectedCategory !in categories) selectedCategory = "All"

        categories.forEach { category ->
            chipGroup.addView(
                Chip(requireContext()).apply {
                    text = category
                    isCheckable = true
                    isChecked = category == selectedCategory
                    setOnClickListener {
                        selectedCategory = category
                        applyFilters()
                    }
                }
            )
        }
    }

    private fun applyFilters() {
        if (!::adapter.isInitialized) return

        val query = searchInput.text.toString().trim().lowercase()

        val result = menuItems.filter { item ->
            val categoryOk =
                selectedCategory == "All" ||
                        item.categoryName.orEmpty().equals(selectedCategory, ignoreCase = true)

            val searchOk =
                query.isBlank() ||
                        item.itemName.lowercase().contains(query) ||
                        item.categoryName.orEmpty().lowercase().contains(query)

            categoryOk && searchOk
        }

        filteredMenuItems.clear()
        filteredMenuItems.addAll(result)
        adapter.notifyDataSetChanged()
    }

    private fun buildDraftItems(): List<FoodOrderItemEntity> {
        return quantities.mapNotNull { (menuItemRemoteId, qty) ->
            if (qty <= 0) return@mapNotNull null

            val menuItem = menuItems.firstOrNull { it.remoteId == menuItemRemoteId }
                ?: return@mapNotNull null

            val resolvedGstRate = menuItem.gstRatePercent

            FoodOrderItemEntity(
                remoteId = "",
                hotelRemoteId = repository.hotelRemoteId,
                orderRemoteId = "",
                menuItemRemoteId = menuItem.remoteId,
                itemName = menuItem.itemName,
                quantity = qty.toDouble(),
                unitPrice = menuItem.price,
                gstCategoryRemoteId = menuItem.gstCategoryRemoteId,
                gstCategoryName = menuItem.gstCategoryName,
                hsnSacCode = menuItem.hsnSacCode,
                gstRatePercent = menuItem.gstRatePercent,
                cgstRatePercent = menuItem.cgstRatePercent,
                sgstRatePercent = menuItem.sgstRatePercent,
                cessRatePercent = menuItem.cessRatePercent,
                syncState = "PENDING"
            )
        }
    }

    private fun updateCart() {
        if (!::cartItemsText.isInitialized || !::cartAmountText.isInitialized) return

        val items = buildDraftItems()
        val totalQty = items.sumOf { it.quantity }.toInt()
        val amount = items.sumOf { it.quantity * it.unitPrice }

        cartItemsText.text = "$totalQty Item${if (totalQty == 1) "" else "s"}"
        cartAmountText.text = "₹${money(amount)}"
        previewButton.isEnabled = totalQty > 0
        previewButton.alpha = if (totalQty > 0) 1f else 0.5f
    }

    private fun showPreview(draftItems: List<FoodOrderItemEntity>) {
        val selectedIndex = roomSpinner.selectedItemPosition
        val target = orderTargets.getOrNull(selectedIndex)
            ?: FoodOrderTarget(label = "Table Sale", booking = null, room = null)
        val room = target.room

        val roomName = room?.roomName ?: "Table Sale"
        val guestName = guestInput.text.toString().trim().ifEmpty {
            target.booking?.guestName?.takeIf { it.isNotBlank() } ?: roomName
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 8)
        }

        layout.addView(TextView(requireContext()).apply {
            text = roomName
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })

        layout.addView(TextView(requireContext()).apply {
            text = guestName
            textSize = 14f
            setPadding(0, 4, 0, 16)
        })

        draftItems.forEach { item ->
            layout.addView(TextView(requireContext()).apply {
                text = "${item.itemName} x ${money(item.quantity)}     ₹${money(item.quantity * item.unitPrice)}"
                textSize = 15f
                setPadding(0, 8, 0, 8)
            })
        }

        val total = draftItems.sumOf { it.quantity * it.unitPrice }

        layout.addView(TextView(requireContext()).apply {
            text = "Total ₹${money(total)}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 0)
        })

        AlertDialog.Builder(requireContext())
            .setTitle("Preview Order")
            .setView(ScrollView(requireContext()).apply { addView(layout) })
            .setNeutralButton("Save KOT") { _, _ ->
                saveOrder(target.booking, room, guestName, draftItems, FoodOrderStatus.KOT)
            }
            .setPositiveButton("Finalize") { _, _ ->
                saveOrder(target.booking, room, guestName, draftItems, FoodOrderStatus.FINALIZED)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun saveOrder(
        booking: BookingEntity?,
        room: RoomEntity?,
        guestName: String,
        draftItems: List<FoodOrderItemEntity>,
        status: String
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.saveFoodOrder(
                existing = null,
                booking = booking,
                room = room,
                guestName = guestName,
                discountAmount = 0.0,
                notes = null,
                status = status,
                items = draftItems
            )

            when (result) {
                is SaveResult.Success -> {
                    Toast.makeText(requireContext(), "Food order saved", Toast.LENGTH_SHORT).show()
                    quantities.clear()
                    adapter.notifyDataSetChanged()
                    updateCart()
                }

                is SaveResult.Error ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()

                is SaveResult.Conflict ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun money(value: Double): String = moneyFormat.format(value)
}

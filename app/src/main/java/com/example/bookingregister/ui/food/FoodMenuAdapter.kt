package com.example.bookingregister.ui.food

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bookingregister.R
import com.example.bookingregister.data.entities.FoodMenuItemEntity

class FoodMenuAdapter(
    private val items: MutableList<FoodMenuItemEntity>,
    private val quantities: MutableMap<String, Int>,
    private val onQuantityChanged: () -> Unit
) : RecyclerView.Adapter<FoodMenuAdapter.MenuViewHolder>() {

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvQty: TextView = view.findViewById(R.id.tvQty)
        val btnPlus: ImageButton = view.findViewById(R.id.btnPlus)
        val btnMinus: ImageButton = view.findViewById(R.id.btnMinus)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_menu_row, parent, false)

        return MenuViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: MenuViewHolder,
        position: Int
    ) {
        val item = items[position]

        holder.tvItemName.text = item.itemName
        holder.tvPrice.text = "₹${item.price.toInt()}"

        holder.tvQty.text =
            (quantities[item.remoteId] ?: 0).toString()

        holder.btnPlus.setOnClickListener {

            val newQty =
                (quantities[item.remoteId] ?: 0) + 1

            quantities[item.remoteId] = newQty

            holder.tvQty.text = newQty.toString()

            onQuantityChanged()
        }

        holder.btnMinus.setOnClickListener {

            val current =
                quantities[item.remoteId] ?: 0

            if (current <= 0) return@setOnClickListener

            val newQty = current - 1

            quantities[item.remoteId] = newQty

            holder.tvQty.text = newQty.toString()

            onQuantityChanged()
        }
    }

    fun updateData(
        newItems: List<FoodMenuItemEntity>
    ) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }


}

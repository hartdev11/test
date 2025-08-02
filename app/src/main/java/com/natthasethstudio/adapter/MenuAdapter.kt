package com.natthasethstudio.sethpos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.natthasethstudio.sethpos.R
import com.natthasethstudio.sethpos.model.MenuDataItem
import com.google.android.material.button.MaterialButton
import android.widget.ImageButton

class MenuAdapter(
    private var items: MutableList<MenuDataItem>,
    private val onItemClick: (MenuDataItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvItemName)
        val priceText: TextView = view.findViewById(R.id.tvItemPrice)
        val imageView: ImageView = view.findViewById(R.id.ivItemImage)
        val addToCartButton: ImageButton = view.findViewById(R.id.btnAddToCart)
        val categoryText: TextView = view.findViewById(R.id.tvItemCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = items[position]
        holder.nameText.text = item.name
        holder.categoryText.text = item.category
        holder.priceText.text = holder.itemView.context.getString(R.string.price_format, item.price)
        holder.priceText.visibility = android.view.View.VISIBLE
        // โหลดรูปภาพด้วย Glide
        if (!item.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_add_image)
                .error(R.drawable.ic_add_image)
                .centerCrop()
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.ic_add_image)
        }
        holder.addToCartButton.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MenuDataItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getPosition(item: MenuDataItem): Int {
        return items.indexOf(item)
    }
}
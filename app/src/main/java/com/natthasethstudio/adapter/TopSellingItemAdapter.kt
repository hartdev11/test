package com.natthasethstudio.sethpos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.R

class TopSellingItemAdapter(
    private val items: List<Pair<String, Int>>
) : RecyclerView.Adapter<TopSellingItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_selling, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvRank.text = "${position + 1}."
        holder.tvName.text = item.first
        holder.tvQuantity.text = "จำนวน: ${item.second}"
    }

    override fun getItemCount() = items.size
} 
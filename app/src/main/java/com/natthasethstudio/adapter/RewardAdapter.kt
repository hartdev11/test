package com.natthasethstudio.sethpos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.R
import com.natthasethstudio.sethpos.model.Reward
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class RewardAdapter(private val onRewardClick: (Reward) -> Unit) : ListAdapter<Reward, RewardAdapter.RewardViewHolder>(RewardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageReward: ImageView = itemView.findViewById(R.id.imageReward)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)
        private val buttonClaim: MaterialButton = itemView.findViewById(R.id.buttonClaim)
        private val textExpiry: TextView = itemView.findViewById(R.id.textExpiry)

        fun bind(reward: Reward) {
            textTitle.text = reward.title
            textDescription.text = reward.description

            if (reward.isClaimed) {
                buttonClaim.text = "ดูรายละเอียด"
                buttonClaim.isEnabled = true
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val expiresDate = reward.expiresAt?.let { dateFormat.format(it) } ?: "-"
                textExpiry.text = "หมดอายุ: $expiresDate"
                textExpiry.visibility = View.VISIBLE
            } else {
                buttonClaim.text = if (reward.isAvailable) "รับรางวัล" else "ยังไม่พร้อม"
                buttonClaim.isEnabled = reward.isAvailable
                textExpiry.visibility = View.GONE
            }

            buttonClaim.setOnClickListener {
                onRewardClick(reward)
            }
        }
    }

    private class RewardDiffCallback : DiffUtil.ItemCallback<Reward>() {
        override fun areItemsTheSame(oldItem: Reward, newItem: Reward): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reward, newItem: Reward): Boolean {
            return oldItem == newItem
        }
    }
} 
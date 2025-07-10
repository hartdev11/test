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
import com.natthasethstudio.sethpos.model.Notification
import java.util.*

class NotificationAdapter : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    var onNotificationClickListener: ((Notification) -> Unit)? = null
    private var unreadCount = 0
    private var itemView: View? = null

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewNotification: TextView = itemView.findViewById(R.id.textViewNotificationText)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewNotificationTime)
        private val viewUnreadIndicator: View = itemView.findViewById(R.id.viewUnreadIndicator)
        private val imageViewSenderProfile: ImageView = itemView.findViewById(R.id.imageViewSenderProfile)
        private val imageViewNotificationType: ImageView = itemView.findViewById(R.id.imageViewNotificationType)

        fun bind(notification: Notification) {
            // Set notification text based on type
            val notificationText = when (notification.type) {
                "like" -> "${notification.senderName} ชอบโพสต์ของคุณ"
                "comment" -> "${notification.senderName} แสดงความคิดเห็นในโพสต์ของคุณ"
                "follow" -> "${notification.senderName} เริ่มติดตามคุณ"
                else -> notification.message
            }
            textViewNotification.text = notificationText

            // Set timestamp
            val date = notification.timestamp.toDate()
            val now = Date()
            val diff = now.time - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            val timeAgo = when {
                days > 0 -> "$days วันที่แล้ว"
                hours > 0 -> "$hours ชั่วโมงที่แล้ว"
                minutes > 0 -> "$minutes นาทีที่แล้ว"
                else -> "เมื่อสักครู่"
            }
            textViewTimestamp.text = timeAgo

            // Set unread indicator
            viewUnreadIndicator.visibility = if (notification.read) View.GONE else View.VISIBLE

            // Set notification type icon
            val typeIcon = when (notification.type) {
                "like" -> R.drawable.ic_heart
                "comment" -> R.drawable.comment_dots
                "follow" -> R.drawable.ic_person_add
                else -> R.drawable.ic_notifications
            }
            imageViewNotificationType.setImageResource(typeIcon)

            // Set sender's profile image
            val avatarId = notification.senderAvatarId
            val avatarResources = listOf(
                R.drawable.avatar_1,
                R.drawable.avatar_2,
                R.drawable.avatar_3,
                R.drawable.avatar_4,
                R.drawable.avatar_5,
                R.drawable.avatar_6
            )
            imageViewSenderProfile.setImageResource(avatarResources[avatarId])

            // Set click listener
            itemView.setOnClickListener {
                onNotificationClickListener?.invoke(notification)
                if (!notification.read) {
                    notification.read = true
                    viewUnreadIndicator.visibility = View.GONE
                    unreadCount--
                    updateUnreadCount()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        itemView = view
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<Notification>?) {
        unreadCount = list?.count { !it.read } ?: 0
        updateUnreadCount()
        super.submitList(list)
    }

    private fun updateUnreadCount() {
        val activity = (itemView?.context as? android.app.Activity)
        val notificationCountView = activity?.findViewById<TextView>(R.id.textNotificationCount)
        notificationCountView?.let {
            if (unreadCount > 0) {
                it.text = unreadCount.toString()
                it.visibility = View.VISIBLE
            } else {
                it.visibility = View.GONE
            }
        }
    }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.notificationId == newItem.notificationId
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
    }
} 
package com.natthasethstudio.sethpos.adapter

import android.util.Log
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
import com.natthasethstudio.sethpos.SethPOSApplication
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import java.util.*

class NotificationAdapter : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    var onNotificationClickListener: ((Notification) -> Unit)? = null
    private var unreadCount = 0
    private var itemView: View? = null
    private val firestore = FirebaseFirestore.getInstance()
    // userId -> Triple<name, avatarId, photoUrl>
    private val senderCache = mutableMapOf<String, Triple<String, Int, String?>>()

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewNotification: TextView = itemView.findViewById(R.id.textViewNotificationText)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewNotificationTime)
        private val viewUnreadIndicator: View = itemView.findViewById(R.id.viewUnreadIndicator)
        private val imageViewSenderProfile: ShapeableImageView = itemView.findViewById(R.id.imageViewSenderProfile)
        private val imageViewNotificationType: ImageView = itemView.findViewById(R.id.imageViewNotificationType)

        fun bind(notification: Notification) {
            // Set card background based on read status
            itemView.alpha = if (notification.read) 0.8f else 1.0f
            
            loadSenderDetails(notification) { senderName, avatarId, photoUrl ->
                // Set notification text based on type with correct sender name
                val notificationText = when (notification.type) {
                    "like" -> "$senderName ชอบโพสต์ของคุณ"
                    "comment" -> "$senderName แสดงความคิดเห็นในโพสต์ของคุณ"
                    "boost" -> "$senderName ได้บูสต์โพสต์ของคุณ"
                    "follow" -> "$senderName เริ่มติดตามคุณ"
                    else -> notification.message
                }
                textViewNotification.text = notificationText

                // Set sender's profile image: Gmail photoUrl > avatar
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(imageViewSenderProfile.context)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_default_user)
                        .error(R.drawable.ic_default_user)
                        .into(imageViewSenderProfile)
                } else {
                    val avatarResources = listOf(
                        R.drawable.avatar_1,
                        R.drawable.avatar_2,
                        R.drawable.avatar_3,
                        R.drawable.avatar_4,
                        R.drawable.avatar_5,
                        R.drawable.avatar_6
                    )
                    val safeAvatarId = if (avatarId in 0 until avatarResources.size) avatarId else 0
                    imageViewSenderProfile.setImageResource(avatarResources[safeAvatarId])
                }
            }

            // Set timestamp with better formatting
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

            // Set unread indicator with animation
            if (notification.read) {
                viewUnreadIndicator.visibility = View.GONE
            } else {
                viewUnreadIndicator.visibility = View.VISIBLE
                viewUnreadIndicator.alpha = 0f
                viewUnreadIndicator.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }

            // Set notification type icon with color tinting
            val (typeIcon, iconColor) = when (notification.type) {
                "like" -> Pair(R.drawable.ic_heart, R.color.like_red)
                "comment" -> Pair(R.drawable.comment_dots, R.color.colorAccent)
                "boost" -> Pair(R.drawable.ic_fire, R.color.boost_color)
                "follow" -> Pair(R.drawable.ic_person_add, R.color.success)
                else -> Pair(R.drawable.ic_notifications, R.color.colorAccent)
            }
            imageViewNotificationType.setImageResource(typeIcon)
            imageViewNotificationType.setColorFilter(
                itemView.context.getColor(iconColor),
                android.graphics.PorterDuff.Mode.SRC_IN
            )

            // Set click listener with ripple effect
            itemView.setOnClickListener {
                // Add click animation
                itemView.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()

                onNotificationClickListener?.invoke(notification)
                
                if (!notification.read) {
                    notification.read = true
                    
                    // Animate unread indicator fade out
                    viewUnreadIndicator.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            viewUnreadIndicator.visibility = View.GONE
                        }
                        .start()
                    
                    // Animate card fade
                    itemView.animate()
                        .alpha(0.8f)
                        .setDuration(300)
                        .start()
                    
                    unreadCount--
                    updateUnreadCount()
                    
                    // Mark as read in Firestore
                    try {
                        firestore.collection("notifications")
                            .document(notification.notificationId)
                            .update("read", true)
                            .addOnSuccessListener {
                                // Update main activity header to hide badge
                                try {
                                    val mainActivity = (itemView.context.applicationContext as? SethPOSApplication)?.feedAdapter
                                    mainActivity?.updateHeaderProfile()
                                } catch (e: Exception) {
                                    Log.e("NotificationAdapter", "Error updating header: ${e.message}")
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e("NotificationAdapter", "Error marking notification as read: ${error.message}")
                            }
                    } catch (e: Exception) {
                        Log.e("NotificationAdapter", "Error in mark as read: ${e.message}")
                    }
                }
            }
        }

        private fun loadSenderDetails(notification: Notification, onComplete: (String, Int, String?) -> Unit) {
            if (notification.senderId.isEmpty()) {
                onComplete("ไม่พบชื่อ", 0, null)
                return
            }
            // Check cache first
            senderCache[notification.senderId]?.let { (cachedName, cachedAvatarId, cachedPhotoUrl) ->
                onComplete(cachedName, cachedAvatarId, cachedPhotoUrl)
                return
            }
            try {
                firestore.collection("users").document(notification.senderId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val senderName = document.getString("nickname") 
                                ?: document.getString("name") 
                                ?: "ไม่พบชื่อ"
                            val avatarId = document.getLong("avatarId")?.toInt() ?: 0
                            val photoUrl = document.getString("photoUrl")
                            // Cache the result
                            senderCache[notification.senderId] = Triple(senderName, avatarId, photoUrl)
                            onComplete(senderName, avatarId, photoUrl)
                        } else {
                            Log.w("NotificationAdapter", "Sender document not found: ${notification.senderId}")
                            onComplete("ไม่พบชื่อ", 0, null)
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.e("NotificationAdapter", "Error loading sender details: ${error.message}")
                        onComplete("ไม่พบชื่อ", 0, null)
                    }
            } catch (e: Exception) {
                Log.e("NotificationAdapter", "Error in loadSenderDetails: ${e.message}")
                onComplete("ไม่พบชื่อ", 0, null)
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
                it.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                it.visibility = View.VISIBLE
                // Add scale animation
                it.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction {
                        it.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            } else {
                it.visibility = View.GONE
            }
        }
    }

    // Function to clear cache when needed
    fun clearSenderCache() {
        senderCache.clear()
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
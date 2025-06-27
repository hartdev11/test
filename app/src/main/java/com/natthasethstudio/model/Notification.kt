package com.natthasethstudio.sethpos.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Notification(
    @DocumentId val notificationId: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarId: Int = 0,
    val type: String = "", // e.g., "like", "comment", "follow"
    val message: String = "",
    val postId: String? = null, // Optional: if notification is related to a post
    val timestamp: Timestamp = Timestamp.now(),
    var read: Boolean = false,
    // Add more fields as needed, e.g., commentText, followRequestAccepted, etc.
) 
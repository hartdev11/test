package com.natthasethstudio.sethpos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

// Data class to hold cached user info for comments
data class CommentUserCacheEntry(
    val avatarId: Int? = null,
    val displayName: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null
)

class CommentAdapter(private val comments: List<Comment>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val avatarResources = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6
    )

    // Cache for comment author info
    private val commentUserCache = mutableMapOf<String, CommentUserCacheEntry>()

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewCommentProfile: ImageView = itemView.findViewById(R.id.imageViewCommentProfile)
        val textViewCommentDisplayName: TextView = itemView.findViewById(R.id.textViewCommentDisplayName)
        val textViewCommentText: TextView = itemView.findViewById(R.id.textViewCommentText)
        val textViewCommentTime: TextView = itemView.findViewById(R.id.textViewCommentTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.textViewCommentText.text = comment.commentText
        holder.textViewCommentTime.text = comment.commentTime

        // ตั้งค่าชื่อและรูปภาพเริ่มต้นจากข้อมูลที่มีใน comment object
        holder.textViewCommentDisplayName.text = comment.nickname ?: comment.displayName ?: "กำลังโหลด..."
        if (comment.avatarId != null && comment.avatarId in 0..5) {
            Glide.with(holder.imageViewCommentProfile.context)
                .load(avatarResources[comment.avatarId])
                .circleCrop()
                .into(holder.imageViewCommentProfile)
        } else if (!comment.profileImageUrl.isNullOrEmpty()) {
            Glide.with(holder.imageViewCommentProfile.context)
                .load(comment.profileImageUrl)
                .circleCrop()
                .into(holder.imageViewCommentProfile)
        } else {
            holder.imageViewCommentProfile.setImageResource(R.drawable.ic_default_user)
        }

        // โหลดข้อมูลผู้ใช้จาก Firestore
        loadCommentUserInfo(comment.userId, holder.imageViewCommentProfile, holder.textViewCommentDisplayName)
    }

    override fun getItemCount(): Int = comments.size

    private fun loadCommentUserInfo(userId: String?, imageView: ImageView, userNameTextView: TextView) {
        if (userId == null) {
            imageView.setImageResource(R.drawable.ic_default_user)
            userNameTextView.text = "ไม่พบชื่อ"
            return
        }

        // ตรวจสอบแคชก่อน
        commentUserCache[userId]?.let { cachedInfo ->
            updateUserUI(imageView, userNameTextView, cachedInfo)
            return
        }

        // โหลดข้อมูลจาก Firestore ถ้าไม่มีในแคช
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val avatarId = documentSnapshot.getLong("avatarId")?.toInt()
                    val name = documentSnapshot.getString("name")
                    val nickname = documentSnapshot.getString("nickname")
                    val profileImageUrl = documentSnapshot.getString("profileImageUrl")

                    // บันทึกลงแคช
                    val userInfo = CommentUserCacheEntry(avatarId, name, nickname, profileImageUrl)
                    commentUserCache[userId] = userInfo

                    // อัพเดท UI
                    updateUserUI(imageView, userNameTextView, userInfo)
                } else {
                    Log.w("CommentAdapter", "User document does not exist for ID: $userId")
                    imageView.setImageResource(R.drawable.ic_default_user)
                    userNameTextView.text = "ไม่พบข้อมูลผู้ใช้"
                }
            }
            .addOnFailureListener { e ->
                Log.e("CommentAdapter", "Error loading user data for $userId", e)
                imageView.setImageResource(R.drawable.ic_default_user)
                userNameTextView.text = "เกิดข้อผิดพลาด"
            }
    }

    private fun updateUserUI(imageView: ImageView, userNameTextView: TextView, userInfo: CommentUserCacheEntry) {
        // ตั้งค่าชื่อ
        userNameTextView.text = when {
            !userInfo.nickname.isNullOrEmpty() -> userInfo.nickname
            !userInfo.displayName.isNullOrEmpty() -> userInfo.displayName
            else -> "ไม่พบชื่อ"
        }

        // โหลดรูปโปรไฟล์
        if (!userInfo.profileImageUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(userInfo.profileImageUrl)
                .circleCrop()
                .into(imageView)
        } else if (userInfo.avatarId != null && userInfo.avatarId in 0..5) {
            Glide.with(imageView.context)
                .load(avatarResources[userInfo.avatarId])
                .circleCrop()
                .into(imageView)
        } else {
            Glide.with(imageView.context)
                .load(R.drawable.ic_default_user)
                .circleCrop()
                .into(imageView)
        }
    }

    // ฟังก์ชันล้างแคชข้อมูลผู้ใช้
    fun clearCommentUserCache() {
        commentUserCache.clear()
    }
}
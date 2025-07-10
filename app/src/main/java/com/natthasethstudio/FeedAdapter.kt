package com.natthasethstudio.sethpos

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.button.MaterialButton
import android.widget.ImageButton
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.natthasethstudio.sethpos.util.AvatarResources
import android.util.Log
import android.view.animation.AnimationUtils
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import com.google.firebase.firestore.SetOptions
import com.natthasethstudio.sethpos.StoreProfileActivity
import com.google.android.material.chip.Chip

// Data class to hold cached user info
data class UserCacheEntry(
    val avatarId: Int? = null,
    val nickname: String? = null,
    val isPremium: Boolean = false,
    val isStore: Boolean = false,
    val storeName: String? = null,
    val profileImageUrl: String? = null,
    val displayName: String? = null
)

class FeedAdapter(
    private var currentUserId: String?,
    private val processingLikePostIds: MutableSet<String>,
    private val onLikeClickListener: (Post, Int) -> Unit,
    private val onCommentClickListener: (Post, Int) -> Unit,
    private val onAnimalInteraction: ((String) -> Unit)? = null,
    private val onHeaderEvent: ((HeaderEvent) -> Unit)? = null
) : ListAdapter<Post, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 100
        private const val VIEW_TYPE_POST = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val userCache = mutableMapOf<String, UserCacheEntry>()
    private val boostCache = mutableMapOf<String, Pair<Int, List<String>>>() // Cache for boost data
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false
    private var isFetchingBoost = false
    private var isBoostProcessing = false
    private val boostProcessingLock = Any() // เพิ่ม lock object สำหรับการซิงค์
    private val boostProcessingMap = mutableMapOf<String, Boolean>() // เพิ่ม map สำหรับติดตามการบูสต์แต่ละโพสต์
    private val boostDebounceTime = 500L // 500ms debounce time
    private val boostDebounceMap = mutableMapOf<String, Long>()
    private var isLoading = false
    var currentFilterState = true // true = all, false = nearMe - make it public

    sealed class HeaderEvent {
        object HomeClick : HeaderEvent()
        object CheckinClick : HeaderEvent()
        object ProfileClick : HeaderEvent()
        object CreatePostClick : HeaderEvent()
        object AddPhotoClick : HeaderEvent()
        object ChipAllClick : HeaderEvent()
        object ChipNearMeClick : HeaderEvent()
        object NotificationClick : HeaderEvent()
    }

    // Function to get item at position
    override fun getItem(position: Int): Post {
        // Adjust position for header (position 0 is header, so subtract 1 for posts)
        return currentList[position - 1]
    }

    // Function to update current user ID
    fun setCurrentUserId(userId: String?) {
        this.currentUserId = userId
        notifyItemRangeChanged(0, itemCount)
    }

    // Function to clear all caches
    fun clearAllCaches() {
        userCache.clear()
        boostCache.clear()
        boostProcessingMap.clear()
    }

    // Function to update like button state for a specific post
    fun updateLikeButtonStateForPost(postId: String) {
        val position = currentList.indexOfFirst { it.postId == postId }
        if (position != -1) {
            notifyItemChanged(position + 1) // +1 because of header
        }
    }

    fun setLoading(loading: Boolean) {
        val oldLoading = isLoading
        isLoading = loading
        if (oldLoading != loading) {
            if (loading) {
                notifyItemInserted(itemCount)
            } else {
                notifyItemRemoved(itemCount)
            }
        }
    }

    override fun getItemCount(): Int {
        // +1 for header
        return super.getItemCount() + 1 + if (isLoading) 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> VIEW_TYPE_HEADER
            isLoading && position == itemCount - 1 -> VIEW_TYPE_LOADING
            else -> VIEW_TYPE_POST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_feed_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.loading_state, parent, false)
                LoadingViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed_post, parent, false)
                PostViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                bindHeaderViewHolder(holder)
            }
            is PostViewHolder -> {
                val post = getItem(position)
                bindPostViewHolder(holder, post, position)
            }
            is LoadingViewHolder -> {
                // Loading view doesn't need binding
            }
        }
    }

    private fun bindPostViewHolder(holder: PostViewHolder, post: Post, position: Int) {
        Log.d("FeedAdapter", "Binding post at position $position, userId: ${post.userId}")
        
        // Display nickname if available from post object, otherwise display full name
        if (!post.nickname.isNullOrEmpty()) {
            holder.userName.text = post.nickname
        } else {
            holder.userName.text = post.displayName
        }
        holder.postTime.text = post.postTime

        // Enhanced post content display
        val postText = post.postText
        if (!postText.isNullOrEmpty()) {
            holder.postContent.isVisible = true
            holder.postContent.text = postText
        } else {
            holder.postContent.isVisible = false
        }

        // Enhanced like button state
        updateLikeButtonState(holder, post)

        // Enhanced comment count display
        updateCommentCount(holder, post)

        // Load user avatar and name with caching
        post.userId?.let { userId ->
            loadUserAvatarAndName(userId, holder.userImage, holder.userName, holder.crownIcon, holder.verifiedBadge)
        } ?: run {
            holder.userImage.setImageResource(R.drawable.ic_default_user)
            holder.userName.text = "ไม่พบชื่อ"
            holder.crownIcon.visibility = View.GONE
            holder.verifiedBadge.visibility = View.GONE
        }

        // Enhanced image loading with placeholder and transition
        val imageUrl = post.postImageUrl
        if (!imageUrl.isNullOrEmpty()) {
            holder.postImage.isVisible = true
            loadPostImage(holder.postImage, imageUrl)
        } else {
            holder.postImage.isVisible = false
        }

        // Update boost button state
        fetchBoostData(post, holder)

        // Set up click listeners with ripple effects
        Log.d("FeedAdapter", "Setting up click listeners for post at position $position")
        setupClickListeners(holder, post, position)
    }

    private fun fetchBoostData(post: Post, holder: PostViewHolder) {
        val postId = post.postId ?: return
        
        // ตรวจสอบแคชก่อน
        boostCache[postId]?.let { (count, users) ->
            post.boostCount = count
            post.isBoosted = currentUserId != null && users.contains(currentUserId)
            updateBoostUI(holder, post)
            return
        }

        // ถ้าไม่มีในแคช ให้ดึงข้อมูลจาก Firestore
        firestore.collection("postBoosts").document(postId)
            .get()
            .addOnSuccessListener { doc ->
                val boostCount = doc.getLong("boostCount")?.toInt() ?: 0
                val boostedUsers = (doc.get("boostedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                
                // อัพเดทข้อมูล
                post.boostCount = boostCount
                post.isBoosted = currentUserId != null && boostedUsers.contains(currentUserId)
                
                // อัพเดทแคช
                boostCache[postId] = Pair(boostCount, boostedUsers)
                
                // อัพเดท UI
                updateBoostUI(holder, post)
            }
            .addOnFailureListener { e ->
                Log.e("FeedAdapter", "Error fetching boost data: ${e.message}")
            }
    }

    private fun updateLikeButtonState(holder: PostViewHolder, post: Post) {
        Log.d("FeedAdapter", "Updating like button state for post: ${post.postId}, isLiked: ${post.isLiked}, likeCount: ${post.likeCount}")
        
        holder.btnLike.isEnabled = !processingLikePostIds.contains(post.postId)
        
        // อัพเดทไอคอนและสีของปุ่มไลค์
        if (post.isLiked) {
            holder.btnLike.setIconResource(R.drawable.ic_heart_filled)
            holder.btnLike.iconTint = ContextCompat.getColorStateList(holder.itemView.context, R.color.like_red)
            holder.btnLike.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.like_red))
        } else {
            holder.btnLike.setIconResource(R.drawable.ic_heart_outline)
            holder.btnLike.iconTint = ContextCompat.getColorStateList(holder.itemView.context, R.color.text_primary)
            holder.btnLike.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
        }
        
        // อัพเดทจำนวนไลค์
        val likeCountText = if (post.likeCount > 0) "${post.likeCount}" else ""
        holder.likeCount.text = likeCountText
        
        // แสดงหรือซ่อนจำนวนไลค์
        holder.likeCount.visibility = if (post.likeCount > 0) View.VISIBLE else View.GONE
        
        Log.d("FeedAdapter", "Like button updated - enabled: ${holder.btnLike.isEnabled}, count: $likeCountText")
    }

    private fun updateCommentCount(holder: PostViewHolder, post: Post) {
        if (post.commentCount > 0) {
            holder.viewAllComments.isVisible = true
            holder.viewAllComments.text = holder.itemView.context.getString(R.string.post_comment_count, post.commentCount)
        } else {
            holder.viewAllComments.isVisible = false
        }
    }

    private fun updateBoostUI(holder: PostViewHolder, post: Post) {
        // อัพเดทจำนวนบูสต์
        holder.boostCount.text = post.boostCount.toString()
        holder.boostCount.visibility = if (post.boostCount > 0) View.VISIBLE else View.GONE
        holder.boostLabel?.visibility = if (post.boostCount > 0) View.VISIBLE else View.GONE

        // อัพเดทสีและไอคอนของปุ่มบูสต์
        val context = holder.itemView.context
        if (post.isBoosted) {
            holder.btnBoost.setIconTintResource(R.color.boost_color)
            holder.btnBoost.setTextColor(ContextCompat.getColor(context, R.color.boost_color))
            holder.btnBoost.setIconResource(R.drawable.ic_boost_post_filled)
        } else {
            holder.btnBoost.setIconTintResource(R.color.text_primary)
            holder.btnBoost.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.btnBoost.setIconResource(R.drawable.paper_plane)
        }

        // อัพเดทพื้นหลังของโพสต์
        if (post.boostCount >= 10) {
            holder.itemView.setBackgroundResource(R.drawable.boosted_post_background)
        } else {
            holder.itemView.setBackgroundResource(0)
        }
    }

    private fun handleBoostAction(post: Post, holder: PostViewHolder) {
        val postId = post.postId ?: return
        val currentUserId = currentUserId ?: return

        // ตรวจสอบว่ากำลังบูสต์อยู่หรือไม่
        if (boostProcessingMap[postId] ?: false) return

        // ตั้งค่าสถานะการบูสต์
        boostProcessingMap[postId] = true
        holder.btnBoost.isEnabled = false

        // เพิ่ม haptic feedback
        holder.btnBoost.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

        // อัพเดท UI แบบ optimistic ทันที
        val isCurrentlyBoosted = post.isBoosted
        val currentBoostCount = post.boostCount
        post.isBoosted = !isCurrentlyBoosted
        post.boostCount = if (isCurrentlyBoosted) currentBoostCount - 1 else currentBoostCount + 1
        
        // อัพเดท UI ทันที
        updateBoostUI(holder, post)

        // อัพเดท Firestore
        val boostRef = firestore.collection("postBoosts").document(postId)
        
        // ใช้ transaction เพื่อให้แน่ใจว่าข้อมูลถูกต้อง
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(boostRef)
            val currentBoostedUsers = (snapshot.get("boostedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val newBoostedUsers = if (isCurrentlyBoosted) {
                currentBoostedUsers.filter { it != currentUserId }
            } else {
                currentBoostedUsers + currentUserId
            }
            val newBoostCount = newBoostedUsers.size

            transaction.set(boostRef, mapOf(
                "boostCount" to newBoostCount,
                "boostedUsers" to newBoostedUsers
            ), SetOptions.merge())

            // อัพเดทแคชทันที
            boostCache[postId] = Pair(newBoostCount, newBoostedUsers)
        }.addOnSuccessListener {
            // แสดง Toast แจ้งเตือน
            val message = if (isCurrentlyBoosted) {
                holder.itemView.context.getString(R.string.post_boost_cancel)
            } else {
                holder.itemView.context.getString(R.string.post_boost_success)
            }
            Toast.makeText(holder.itemView.context, message, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            // ถ้าเกิดข้อผิดพลาด ให้ย้อนกลับสถานะ
            revertBoostState(post, holder, isCurrentlyBoosted, currentBoostCount)
            Log.e("FeedAdapter", "Error updating boost: ${e.message}")
            Toast.makeText(holder.itemView.context, "เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
        }.addOnCompleteListener {
            // รีเซ็ตสถานะการบูสต์
            boostProcessingMap.remove(postId)
            holder.btnBoost.isEnabled = true
        }
    }

    private fun revertBoostState(post: Post, holder: PostViewHolder, wasBoosted: Boolean, originalCount: Int) {
        post.isBoosted = wasBoosted
        post.boostCount = originalCount
        updateBoostUI(holder, post)
    }

    private fun setupClickListeners(holder: PostViewHolder, post: Post, position: Int) {
        Log.d("FeedAdapter", "Setting up click listeners for userId: ${post.userId}")
        
        // User image click listener
        holder.userImage.setOnClickListener {
            Log.d("FeedAdapter", "User image clicked! userId: ${post.userId}")
            post.userId?.let { userId ->
                Log.d("FeedAdapter", "User image clicked for userId: $userId")
                // ตรวจสอบ role ของ user ก่อน
                firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        if (userDoc.exists()) {
                            val isStore = userDoc.getBoolean("isStore") ?: false
                            Log.d("FeedAdapter", "User data found - isStore: $isStore, userId: $userId")
                            
                            if (isStore) {
                                // ร้านค้า -> StoreProfileActivity
                                Log.d("FeedAdapter", "Navigating to StoreProfileActivity for store: $userId")
                                val intent = Intent(holder.itemView.context, StoreProfileActivity::class.java)
                                intent.putExtra("storeId", userId)
                                holder.itemView.context.startActivity(intent)
                            } else {
                                // ลูกค้า -> ProfileActivity
                                Log.d("FeedAdapter", "Navigating to ProfileActivity for customer: $userId")
                                val intent = Intent(holder.itemView.context, ProfileActivity::class.java)
                                intent.putExtra("userId", userId)
                                holder.itemView.context.startActivity(intent)
                            }
                        } else {
                            // ถ้าไม่พบข้อมูล user ให้เด้งไป ProfileActivity เป็นค่าเริ่มต้น
                            Log.d("FeedAdapter", "User document not found, navigating to ProfileActivity: $userId")
                            val intent = Intent(holder.itemView.context, ProfileActivity::class.java)
                            intent.putExtra("userId", userId)
                            holder.itemView.context.startActivity(intent)
                        }
                    }
                    .addOnFailureListener { e ->
                        // ถ้าเกิด error ให้เด้งไป ProfileActivity เป็นค่าเริ่มต้น
                        Log.e("FeedAdapter", "Error checking user role: ${e.message}")
                        val intent = Intent(holder.itemView.context, ProfileActivity::class.java)
                        intent.putExtra("userId", userId)
                        holder.itemView.context.startActivity(intent)
                    }
            }
        }

        // Double tap to like on post image
        holder.postImage.setOnClickListener {
            if (!isAnimating) {
                if (!post.isLiked) {
                    animateLike(holder)
                    onLikeClickListener.invoke(post, position)
                }
            }
        }

        holder.btnLike.setOnClickListener {
            if (!isAnimating) {
                // Add haptic feedback
                holder.btnLike.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                
                // Animate the like button
                holder.btnLike.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(100)
                    .withEndAction {
                        holder.btnLike.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                // เก็บสถานะปัจจุบันก่อนที่จะส่งไปยัง onLikeClickListener
                val currentLikeState = post.isLiked
                val willBeLiked = !currentLikeState
                
                Log.d("FeedAdapter", "Like button clicked - current state: $currentLikeState, will be: $willBeLiked")
                
                onLikeClickListener.invoke(post, position)
                
                // ส่ง interaction type ที่ถูกต้อง
                val interactionType = if (willBeLiked) "like" else "unlike"
                Log.d("FeedAdapter", "Sending animal interaction: $interactionType")
                onAnimalInteraction?.invoke(interactionType)
            }
        }

        holder.btnComment.setOnClickListener {
            // Add haptic feedback
            holder.btnComment.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onCommentClickListener.invoke(post, position)
            onAnimalInteraction?.invoke("comment")
        }

        holder.viewAllComments.setOnClickListener {
            onCommentClickListener.invoke(post, position)
        }

        holder.btnBoost.setOnClickListener {
            if (!isBoostProcessing) {
                // Get the current state BEFORE updating
                val wasBoosted = post.isBoosted
                val willBeBoosted = !wasBoosted
                val interactionType = if (willBeBoosted) "boost" else "unboost"
                android.util.Log.d("FeedAdapter", "Post ${post.postId}: wasBoosted=$wasBoosted, willBeBoosted=$willBeBoosted, sending interaction: $interactionType")
                android.util.Log.d("FeedAdapter", "DEBUG: About to call handleBoostAction and send $interactionType")
                
                handleBoostAction(post, holder)
                onAnimalInteraction?.invoke(interactionType)
            }
        }

        holder.btnMore.setOnClickListener { view ->
            // Add haptic feedback
            holder.btnMore.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            showPostOptionsMenu(view, post, position)
        }
    }

    private fun animateLike(holder: PostViewHolder) {
        isAnimating = true
        val likeAnimation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.like_animation)
        holder.postImage.startAnimation(likeAnimation)
        
        handler.postDelayed({
            isAnimating = false
        }, likeAnimation.duration)
    }

    private fun showPostOptionsMenu(view: View, post: Post, position: Int) {
        val popupMenu = PopupMenu(view.context, view)
        popupMenu.inflate(R.menu.post_options_menu)

        val isCurrentUserPost = post.userId != null && currentUserId != null && post.userId == currentUserId
        if (isCurrentUserPost) {
            popupMenu.menu.findItem(R.id.menu_report)?.isVisible = false
            popupMenu.menu.findItem(R.id.menu_block)?.isVisible = false
            popupMenu.menu.findItem(R.id.menu_edit)?.isVisible = true
            popupMenu.menu.findItem(R.id.menu_delete)?.isVisible = true
        } else {
            popupMenu.menu.findItem(R.id.menu_report)?.isVisible = true
            popupMenu.menu.findItem(R.id.menu_block)?.isVisible = true
            popupMenu.menu.findItem(R.id.menu_edit)?.isVisible = false
            popupMenu.menu.findItem(R.id.menu_delete)?.isVisible = false
        }

        popupMenu.setOnMenuItemClickListener { item ->
            handlePostOptionClick(item.itemId, post, position, view)
        }

        popupMenu.show()
    }

    private fun handlePostOptionClick(itemId: Int, post: Post, position: Int, view: View): Boolean {
        return when (itemId) {
            R.id.menu_report -> {
                Toast.makeText(view.context, "Report post clicked for ${post.postId}", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_block -> {
                Toast.makeText(view.context, "Block user ${post.userId ?: "unknown"}", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_edit -> {
                val intent = Intent(view.context, CreatePostActivity::class.java)
                intent.putExtra("postId", post.postId)
                view.context.startActivity(intent)
                true
            }
            R.id.menu_delete -> {
                showDeleteConfirmationDialog(post, position, view)
                true
            }
            else -> false
        }
    }

    private fun showDeleteConfirmationDialog(post: Post, position: Int, view: View) {
        AlertDialog.Builder(view.context)
            .setTitle("ลบโพสต์")
            .setMessage("คุณต้องการลบโพสต์นี้ใช่หรือไม่?")
            .setPositiveButton("ลบ") { _, _ ->
                deletePost(post, position, view)
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun deletePost(post: Post, position: Int, view: View) {
        val postId = post.postId ?: return
        firestore.collection("posts").document(postId)
            .delete()
            .addOnSuccessListener {
                // Delete associated comments
                firestore.collection("comments")
                    .whereEqualTo("postId", postId)
                    .get()
                    .addOnSuccessListener { commentsSnapshot ->
                        val batch = firestore.batch()
                        commentsSnapshot.documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                    }

                // Update UI
                val newList = currentList.toMutableList()
                newList.removeAt(position)
                submitList(newList)
                Toast.makeText(view.context, "ลบโพสต์เรียบร้อย", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(view.context, "เกิดข้อผิดพลาดในการลบโพสต์", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserAvatarAndName(userId: String, imageView: ShapeableImageView, nameView: TextView, crownIcon: ImageView, verifiedBadge: ImageView) {
        // Check cache first
        userCache[userId]?.let { cached ->
            Log.d("FeedAdapter", "Using cached data for $userId: profileImageUrl=${cached.profileImageUrl}, displayName=${cached.displayName}")
            updateUserUI(imageView, nameView, crownIcon, verifiedBadge, cached)
            return
        }

        // Load from Firestore
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val isStore = userDoc.getBoolean("isStore") ?: false
                    val storeName = userDoc.getString("storeName")
                    val nickname = userDoc.getString("nickname")
                    val isPremium = userDoc.getBoolean("isPremium") ?: false
                    val avatarId = userDoc.getLong("avatarId")?.toInt()
                    val profileImageUrl = userDoc.getString("profileImageUrl")
                    val displayNameFromDoc = userDoc.getString("displayName")
                    
                    Log.d("FeedAdapter", "User data for $userId: isStore=$isStore, profileImageUrl=$profileImageUrl, displayNameFromDoc=$displayNameFromDoc")

                    // If this is a store, try to get the store image
                    if (isStore) {
                        firestore.collection("stores").document(userId)
                            .get()
                            .addOnSuccessListener { storeDoc ->
                                if (storeDoc.exists()) {
                                    val storeImageUrl = storeDoc.getString("storeImage")
                                    Log.d("FeedAdapter", "Store data for $userId: storeImageUrl=$storeImageUrl")
                                    
                                    val userInfo = UserCacheEntry(
                                        avatarId = avatarId,
                                        nickname = nickname,
                                        isPremium = isPremium,
                                        isStore = true,
                                        storeName = storeName,
                                        profileImageUrl = storeImageUrl ?: profileImageUrl,
                                        displayName = displayNameFromDoc
                                    )
                                    userCache[userId] = userInfo
                                    updateUserUI(imageView, nameView, crownIcon, verifiedBadge, userInfo)
                                } else {
                                    Log.d("FeedAdapter", "No store data found for $userId")
                                    val userInfo = UserCacheEntry(
                                        avatarId = avatarId,
                                        nickname = nickname,
                                        isPremium = isPremium,
                                        isStore = isStore,
                                        storeName = storeName,
                                        profileImageUrl = profileImageUrl,
                                        displayName = displayNameFromDoc
                                    )
                                    userCache[userId] = userInfo
                                    updateUserUI(imageView, nameView, crownIcon, verifiedBadge, userInfo)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("FeedAdapter", "Error loading store data for $userId", e)
                                val userInfo = UserCacheEntry(
                                    avatarId = avatarId,
                                    nickname = nickname,
                                    isPremium = isPremium,
                                    isStore = isStore,
                                    storeName = storeName,
                                    profileImageUrl = profileImageUrl,
                                    displayName = displayNameFromDoc
                                )
                                userCache[userId] = userInfo
                                updateUserUI(imageView, nameView, crownIcon, verifiedBadge, userInfo)
                            }
                    } else {
                        val userInfo = UserCacheEntry(
                            avatarId = avatarId,
                            nickname = nickname,
                            isPremium = isPremium,
                            isStore = isStore,
                            storeName = storeName,
                            profileImageUrl = profileImageUrl,
                            displayName = displayNameFromDoc
                        )
                        userCache[userId] = userInfo
                        updateUserUI(imageView, nameView, crownIcon, verifiedBadge, userInfo)
                    }
                } else {
                    Log.d("FeedAdapter", "No user data found for $userId, trying stores collection")
                    // Try loading from stores collection if user not found
                    firestore.collection("stores").document(userId)
                        .get()
                        .addOnSuccessListener { storeDoc ->
                            if (storeDoc.exists()) {
                                val storeName = storeDoc.getString("storeName")
                                val storeImageUrl = storeDoc.getString("storeImage")
                                
                                Log.d("FeedAdapter", "Found store data for $userId: storeImageUrl=$storeImageUrl")

                                val userInfo = UserCacheEntry(
                                    isStore = true,
                                    storeName = storeName,
                                    profileImageUrl = storeImageUrl,
                                    displayName = storeName
                                )

                                userCache[userId] = userInfo
                                updateUserUI(imageView, nameView, crownIcon, verifiedBadge, userInfo)
                            } else {
                                Log.d("FeedAdapter", "No store data found for $userId")
                                imageView.setImageResource(R.drawable.ic_default_user)
                                nameView.text = "ไม่พบชื่อ"
                                crownIcon.visibility = View.GONE
                                verifiedBadge.visibility = View.GONE
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FeedAdapter", "Error loading store data for $userId", e)
                            imageView.setImageResource(R.drawable.ic_default_user)
                            nameView.text = "ไม่พบชื่อ"
                            crownIcon.visibility = View.GONE
                            verifiedBadge.visibility = View.GONE
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FeedAdapter", "Error loading user data for $userId", e)
                imageView.setImageResource(R.drawable.ic_default_user)
                nameView.text = "ไม่พบชื่อ"
                crownIcon.visibility = View.GONE
                verifiedBadge.visibility = View.GONE
            }
    }

    private fun updateUserUI(imageView: ShapeableImageView, nameView: TextView, crownIcon: ImageView, verifiedBadge: ImageView, userInfo: UserCacheEntry) {
        // Set name
        nameView.text = when {
            !userInfo.displayName.isNullOrEmpty() -> userInfo.displayName
            userInfo.isStore && !userInfo.storeName.isNullOrEmpty() -> userInfo.storeName
            !userInfo.nickname.isNullOrEmpty() -> userInfo.nickname
            else -> "ไม่พบชื่อ"
        }

        // Set premium icon
        crownIcon.visibility = if (userInfo.isPremium) View.VISIBLE else View.GONE

        // Set verified badge for stores
        verifiedBadge.visibility = if (userInfo.isStore) View.VISIBLE else View.GONE

        // Load profile image - Always try to use profileImageUrl first
        Log.d("FeedAdapter", "Updating UI for user: profileImageUrl=${userInfo.profileImageUrl}, avatarId=${userInfo.avatarId}, displayName=${userInfo.displayName}")
        
        if (!userInfo.profileImageUrl.isNullOrEmpty()) {
            Log.d("FeedAdapter", "Loading profile image from URL: ${userInfo.profileImageUrl}")
            Glide.with(imageView.context)
                .load(userInfo.profileImageUrl)
                .placeholder(R.drawable.ic_default_user)
                .error(R.drawable.ic_default_user)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        } else if (userInfo.avatarId != null && userInfo.avatarId in 0..5) {
            Log.d("FeedAdapter", "Using avatar ID: ${userInfo.avatarId}")
            imageView.setImageResource(AvatarResources.avatarList[userInfo.avatarId])
        } else {
            Log.d("FeedAdapter", "Using default user image")
            imageView.setImageResource(R.drawable.ic_default_user)
        }
    }

    private fun loadPostImage(imageView: ShapeableImageView, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_image)
            return
        }

        Glide.with(imageView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .centerCrop()
            .into(imageView)
    }

    fun clearUserCache() {
        userCache.clear()
    }

    class DiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.postId == newItem.postId
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            // เปรียบเทียบเฉพาะข้อมูลที่สำคัญ ไม่รวมข้อมูลที่เปลี่ยนแปลงบ่อย
            return oldItem.postId == newItem.postId &&
                   oldItem.postText == newItem.postText &&
                   oldItem.postImageUrl == newItem.postImageUrl &&
                   oldItem.nickname == newItem.nickname &&
                   oldItem.displayName == newItem.displayName &&
                   oldItem.postTime == newItem.postTime
                   // ไม่รวม likeCount, isLiked, commentCount, boostCount, isBoosted 
                   // เพื่อป้องกันการเด้งไปด้านบนเมื่อมีการเปลี่ยนแปลงข้อมูลเหล่านี้
        }
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userImage: ShapeableImageView = itemView.findViewById(R.id.userImage)
        val userName: TextView = itemView.findViewById(R.id.userName)
        val postTime: TextView = itemView.findViewById(R.id.postTime)
        val postImage: ShapeableImageView = itemView.findViewById(R.id.postImage)
        val postContent: TextView = itemView.findViewById(R.id.postContent)
        val btnLike: MaterialButton = itemView.findViewById(R.id.btnLike)
        val btnComment: MaterialButton = itemView.findViewById(R.id.btnComment)
        val btnBoost: MaterialButton = itemView.findViewById(R.id.btnBoost)
        val likeCount: TextView = itemView.findViewById(R.id.likeCount)
        val viewAllComments: TextView = itemView.findViewById(R.id.viewAllComments)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
        val crownIcon: ImageView = itemView.findViewById(R.id.crownIcon)
        val verifiedBadge: ImageView = itemView.findViewById(R.id.verifiedBadge)
        val boostCount: TextView = itemView.findViewById(R.id.boostCount)
        val boostLabel: TextView? = itemView.findViewById(R.id.boostLabel)
        
        init {
            Log.d("FeedAdapter", "PostViewHolder created, userImage: $userImage")
            Log.d("FeedAdapter", "userImage is clickable: ${userImage.isClickable}")
            Log.d("FeedAdapter", "userImage is focusable: ${userImage.isFocusable}")
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ShapeableImageView = itemView.findViewById(R.id.profileImageMain)
        val profileImageCreatePost: ShapeableImageView = itemView.findViewById(R.id.profileImageCreatePost)
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userStatus: TextView = itemView.findViewById(R.id.userStatus)
        val tvStoreName: TextView = itemView.findViewById(R.id.tvStoreName)
        val btnNotifications: ImageButton = itemView.findViewById(R.id.btnNotifications)
        val chipAll: Chip = itemView.findViewById(R.id.chipAll)
        val chipNearMe: Chip = itemView.findViewById(R.id.chipNearMe)
        val btnCreatePost: View = itemView.findViewById(R.id.createPostBar)
        val btnAddPhoto: ImageButton = itemView.findViewById(R.id.addPhotoButton)
        val btnHome: View = itemView.findViewById(R.id.topNavHome)
        val btnCheckin: View = itemView.findViewById(R.id.topNavCheckin)
        val btnProfile: View = itemView.findViewById(R.id.topNavProfile)
        
        fun bind(currentUserId: String?, onHeaderEvent: ((HeaderEvent) -> Unit)?, isAllFilter: Boolean = true) {
            // Set chip states
            chipAll.isChecked = isAllFilter
            chipNearMe.isChecked = !isAllFilter
            
            // Load user profile data
            currentUserId?.let { userId ->
                FirebaseFirestore.getInstance().collection("users").document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val displayName = document.getString("displayName") ?: "ผู้ใช้"
                            val status = document.getString("status") ?: ""
                            val storeName = document.getString("storeName") ?: ""
                            val avatarId = document.getLong("avatarId")?.toInt() ?: -1
                            
                            userName.text = displayName
                            userStatus.text = status
                            tvStoreName.text = storeName

                            // Load profile image
                            val profileImageUrl = document.getString("profileImageUrl")
                            if (!profileImageUrl.isNullOrEmpty()) {
                                Glide.with(itemView.context)
                                    .load(profileImageUrl)
                                    .error(R.drawable.ic_profile)
                                    .into(profileImage)
                                Glide.with(itemView.context)
                                    .load(profileImageUrl)
                                    .error(R.drawable.ic_profile)
                                    .into(profileImageCreatePost)
                            } else if (avatarId != -1 && avatarId < AvatarResources.avatarList.size) {
                                profileImage.setImageResource(AvatarResources.avatarList[avatarId])
                                profileImageCreatePost.setImageResource(AvatarResources.avatarList[avatarId])
                            } else {
                                profileImage.setImageResource(R.drawable.ic_profile)
                                profileImageCreatePost.setImageResource(R.drawable.ic_profile)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("HeaderViewHolder", "Error loading user profile: ${e.message}")
                        profileImage.setImageResource(R.drawable.ic_profile)
                        profileImageCreatePost.setImageResource(R.drawable.ic_profile)
                        userName.text = "ผู้ใช้"
                        userStatus.text = ""
                        tvStoreName.text = ""
                    }
            }
            
            // Set up click listeners
            btnHome.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.HomeClick) }
            btnCheckin.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.CheckinClick) }
            btnProfile.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.ProfileClick) }
            btnCreatePost.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.CreatePostClick) }
            btnAddPhoto.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.AddPhotoClick) }
            btnNotifications.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.NotificationClick) }
            chipAll.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.ChipAllClick) }
            chipNearMe.setOnClickListener { onHeaderEvent?.invoke(HeaderEvent.ChipNearMeClick) }
        }
    }

    private fun bindHeaderViewHolder(holder: HeaderViewHolder) {
        holder.bind(currentUserId, onHeaderEvent, currentFilterState)
    }

    fun setFilterState(isAllFilter: Boolean) {
        currentFilterState = isAllFilter
        // Update chip states in header
        notifyItemChanged(0) // Notify header to update
    }

    fun updateHeaderProfile() {
        // Update header profile when user data changes
        notifyItemChanged(0) // Notify header to update
    }
}
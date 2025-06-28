package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.imageview.ShapeableImageView
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.ImageButton
import com.google.firebase.Timestamp
import com.natthasethstudio.sethpos.util.PremiumChecker
import com.natthasethstudio.sethpos.ui.PremiumSubscriptionActivity
import com.bumptech.glide.Glide
import com.natthasethstudio.sethpos.util.AvatarResources
import android.view.View
import com.google.android.gms.tasks.Tasks
import android.widget.ProgressBar
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.android.material.card.MaterialCardView
import com.google.android.gms.tasks.Task
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.natthasethstudio.sethpos.databinding.ActivityCustomerMainBinding
import com.natthasethstudio.sethpos.model.Notification
import com.google.firebase.firestore.FieldValue
import android.view.MenuItem
import com.natthasethstudio.sethpos.Post
import com.natthasethstudio.sethpos.ProfileActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Geocoder
import android.location.Location
import android.location.Address
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.net.Uri
import android.content.Context
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.QuerySnapshot


class CustomerMainActivity : AppCompatActivity() {

    private lateinit var recyclerViewFeed: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val postList = mutableListOf<Post>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String? = null
    private val processingLikePostIds = mutableSetOf<String>()

    // UI elements
    private lateinit var chipAll: Chip
    private lateinit var chipNearMe: Chip
    private lateinit var profileImage: ShapeableImageView
    private lateinit var profileImageCreatePost: ShapeableImageView
    private lateinit var profileInfoContainer: MaterialCardView
    private lateinit var userName: TextView
    private lateinit var userStatus: TextView
    private lateinit var btnNotifications: ImageButton
    private lateinit var tvStoreName: TextView
    private lateinit var topNavigationBar: MaterialCardView
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var emptyFeedTextView: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private var isLoading = false
    private var isLastPage = false
    private var lastVisiblePost: com.google.firebase.firestore.DocumentSnapshot? = null
    private val PAGE_SIZE = 20 // ลดลงเพื่อให้โหลดเร็วขึ้น
    private var currentProvince: String? = null
    private lateinit var loadingIndicator: ProgressBar
    private var currentPage = 0

    // Auth State Listener
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                currentUserId = user.uid
                loadUserProfile()
                feedAdapter.setCurrentUserId(user.uid)
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            Log.e("CustomerMainActivity", "Error in auth state listener: ${e.message}")
            Toast.makeText(this, "เกิดข้อผิดพลาดในการตรวจสอบการเข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // Activity Result Launcher
    private val createPostActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            fetchPostsOptimized(filter = "all", paginate = false)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    private lateinit var binding: ActivityCustomerMainBinding
    private var addPhotoButton: View? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reference Views from Layout
        recyclerViewFeed = binding.recyclerViewFeed
        chipAll = binding.chipAll
        chipNearMe = binding.chipNearMe
        profileImage = binding.profileImageMain
        profileImageCreatePost = binding.profileImageCreatePost
        profileInfoContainer = binding.profileInfoContainer
        userName = binding.userName
        userStatus = binding.userStatus
        btnNotifications = binding.btnNotifications
        tvStoreName = binding.tvStoreName
        topNavigationBar = binding.topNavigationBar
        swipeRefreshLayout = binding.swipeRefreshLayout

        // Initialize views
        val createPostBar = binding.createPostBar
        addPhotoButton = binding.addPhotoButton

        // Initialize top navigation views
        val topNavHome = binding.topNavHome
        val topNavCheckin = binding.topNavCheckin
        val topNavProfile = binding.topNavProfile

        // Set up top navigation click listeners
        topNavHome?.setOnClickListener {
            recyclerViewFeed.smoothScrollToPosition(0)
        }

        topNavCheckin?.setOnClickListener {
            val intent = CheckInActivity.newIntent(this)
            startActivity(intent)
        }

        topNavProfile?.setOnClickListener {
            val intent = ProfileActivity.newIntent(this)
            intent.putExtra("userId", currentUserId)
            startActivity(intent)
        }

        // Set up RecyclerView
        recyclerViewFeed.layoutManager = LinearLayoutManager(this)
        feedAdapter = FeedAdapter(
            currentUserId = currentUserId,
            processingLikePostIds = processingLikePostIds,
            onLikeClickListener = { post, position ->
                toggleLike(post)
            },
            onCommentClickListener = { post, position ->
                val intent = Intent(this, CommentActivity::class.java)
                intent.putExtra("postId", post.postId)
                startActivity(intent)
            }
        )
        recyclerViewFeed.adapter = feedAdapter

        // Store FeedAdapter instance in Application
        (application as? SethPOSApplication)?.feedAdapter = feedAdapter

        // Set click listeners
        val btnGoToPremium = binding.btnGoToPremium
        btnGoToPremium?.setOnClickListener {
            val intent = Intent(this, PremiumSubscriptionActivity::class.java)
            startActivity(intent)
        }

        createPostBar?.setOnClickListener {
            checkPostLimit { canPost ->
                if (canPost) {
                    val intent = Intent(this, CreatePostActivity::class.java)
                    createPostActivityResultLauncher.launch(intent)
                } else {
                    val intent = Intent(this, PremiumSubscriptionActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        addPhotoButton?.setOnClickListener {
            checkPremiumStatus { isPremium ->
                if (isPremium) {
                    val intent = Intent(this, CreatePostActivity::class.java)
                    createPostActivityResultLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "ฟีเจอร์อัปโหลดรูปภาพสำหรับสมาชิกพรีเมียมเท่านั้น", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, PremiumSubscriptionActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // Profile click listeners
        profileImage?.setOnClickListener {
            val intent = ProfileActivity.newIntent(this)
            intent.putExtra("userId", currentUserId)
            startActivity(intent)
        }
        profileInfoContainer?.setOnClickListener {
            val intent = ProfileActivity.newIntent(this)
            intent.putExtra("userId", currentUserId)
            startActivity(intent)
        }

        // Notifications button click listener
        btnNotifications?.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        // Feed filter click listeners
        chipAll?.setOnClickListener {
            chipAll.isChecked = true
            chipNearMe.isChecked = false
            
            // รีเซ็ต province filter
            currentProvince = null
            
            // รีเซ็ต realtime listener เพื่อไม่ใช้ province filter
            resetRealtimeListener()
            
            showLoading()
            fetchPostsOptimized(filter = "all", paginate = false)
        }
        chipNearMe?.setOnClickListener {
            chipAll.isChecked = false
            chipNearMe.isChecked = true
            showLoading()
            
            // เพิ่ม debug เพื่อตรวจสอบโพสต์ใน Firebase
            debugCheckPostsInFirebase()
            
            fetchCurrentProvince()
        }

        // เพิ่ม empty state และ loading indicator
        emptyFeedTextView = TextView(this).apply {
            text = getString(R.string.feed_empty)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            visibility = View.GONE
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        loadingProgressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        // Add views to parent safely
        (recyclerViewFeed.parent as? ViewGroup)?.let { parent ->
            parent.addView(emptyFeedTextView)
            parent.addView(loadingProgressBar)
        }

        swipeRefreshLayout?.setOnRefreshListener {
            refreshFeed()
        }

        recyclerViewFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // ตรวจสอบเงื่อนไขต่างๆ ก่อน
                if (isLoading || isLastPage || !isNetworkAvailable()) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // ตรวจสอบว่าถึงจุดที่ควรโหลดเพิ่มหรือไม่ (เมื่อเหลือ 3 รายการ)
                val threshold = 3
                val isNearEnd = (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - threshold)
                
                if (isNearEnd && postList.size >= PAGE_SIZE) {
                    loadMorePosts()
                }
            }
        })

        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyFeedTextView = findViewById(R.id.emptyFeedTextView)

        // Fetch initial posts
        fetchPostsOptimized(filter = "all", paginate = false)
        setupRealtimeListener()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
        
        // Refresh feed on start to get latest data
        // แต่ไม่ต้อง refresh ถ้ามีโพสต์อยู่แล้ว
        if (postList.isEmpty()) {
            refreshFeed()
        }
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun loadUserProfile() {
        try {
            currentUserId?.let { userId ->
                firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document.exists()) {
                                val displayName = document.getString("displayName") ?: "ผู้ใช้"
                                val status = document.getString("status") ?: ""
                                val storeName = document.getString("storeName") ?: ""
                                val avatarId = document.getLong("avatarId")?.toInt() ?: -1 // Get avatarId
                                
                                userName.text = displayName
                                userStatus.text = status
                                tvStoreName.text = storeName

                                // Load profile image safely
                                val profileImageUrl = document.getString("profileImageUrl")
                                if (!profileImageUrl.isNullOrEmpty()) {
                                    try {
                                        Glide.with(this)
                                            .load(profileImageUrl)
                                            .error(R.drawable.ic_profile)
                                            .into(profileImage)
                                        Glide.with(this)
                                            .load(profileImageUrl)
                                            .error(R.drawable.ic_profile)
                                            .into(profileImageCreatePost)
                                    } catch (e: Exception) {
                                        Log.e("CustomerMainActivity", "Error loading profile image: ${e.message}")
                                        profileImage.setImageResource(R.drawable.ic_profile)
                                        profileImageCreatePost.setImageResource(R.drawable.ic_profile)
                                    }
                                } else if (avatarId != -1 && avatarId < AvatarResources.avatarList.size) {
                                    // If profileImageUrl is empty, use avatarId
                                    profileImage.setImageResource(AvatarResources.avatarList[avatarId])
                                    profileImageCreatePost.setImageResource(AvatarResources.avatarList[avatarId])
                                } else {
                                    // Fallback to default if neither is available
                                    profileImage.setImageResource(R.drawable.ic_profile)
                                    profileImageCreatePost.setImageResource(R.drawable.ic_profile)
                                }

                            }
                        } catch (e: Exception) {
                            Log.e("CustomerMainActivity", "Error processing user data: ${e.message}")
                            Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CustomerMainActivity", "Error loading user profile: ${e.message}")
                        Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("CustomerMainActivity", "Error in loadUserProfile: ${e.message}")
            Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setDefaultProfileImage() {
         profileImage.setImageResource(R.drawable.ic_default_user)
         profileImageCreatePost.setImageResource(R.drawable.ic_default_user)
    }

    private fun loadImage(imageView: ShapeableImageView, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_default_user)
            return
        }

        val requestOptions = com.bumptech.glide.request.RequestOptions()
            .circleCrop()
            .placeholder(R.drawable.ic_default_user)
            .error(R.drawable.ic_default_user)

        Glide.with(imageView.context)
            .load(imageUrl)
            .apply(requestOptions)
            .into(imageView)
    }

    override fun onResume() {
        super.onResume()
        // Reload profile when returning to this activity
        loadUserProfile()
    }

    private fun setDefaultProfile() {
        userName.text = "User"
        userStatus.text = ""
        profileImage.setImageResource(R.drawable.ic_default_user)
        profileImageCreatePost.setImageResource(R.drawable.ic_default_user)
    }

    private fun setupRealtimeListener() {
        // Listener สำหรับโพสต์ - ไม่ใช้ limit เพื่อให้เห็นโพสต์ทั้งหมด
        var query = firestore.collection("posts")
            .orderBy("postTime", Query.Direction.DESCENDING)
        
        // เพิ่ม province filter ถ้ามี
        if (currentProvince != null) {
            query = query.whereEqualTo("province", currentProvince)
            Log.d("CustomerMainActivity", "Realtime listener applying province filter: $currentProvince")
        } else {
            Log.d("CustomerMainActivity", "Realtime listener: no province filter")
        }
        
        val postsListener = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("CustomerMainActivity", "Posts listener failed.", e)
                // แสดง error state ถ้าไม่มีโพสต์อยู่แล้ว
                if (postList.isEmpty()) {
                    showError("เกิดข้อผิดพลาดในการโหลดข้อมูล\nกรุณาลองใหม่อีกครั้ง")
                }
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val fetchedPosts = mutableListOf<Post>()
                val userIds = snapshot.documents.mapNotNull { it.getString("userId") }.toSet().toList()

                if (userIds.isNotEmpty()) {
                    // โหลดข้อมูลผู้ใช้
                    firestore.collection("users")
                        .whereIn("userId", userIds)
                        .get()
                        .addOnSuccessListener { userDocuments ->
                            val userMap = userDocuments.associate { it.getString("userId") to it.getString("nickname") }

                            // โหลดข้อมูลการบูสต์ทั้งหมด
                            val boostTasks = snapshot.documents.map { doc ->
                                firestore.collection("postBoosts").document(doc.id).get()
                            }

                            Tasks.whenAllComplete(boostTasks).addOnSuccessListener {
                                // ประมวลผลโพสต์พร้อมข้อมูลการบูสต์
                                for (document in snapshot.documents) {
                                    val post = document.toObject(Post::class.java)?.apply {
                                        postId = document.id
                                        this.nickname = userMap[this.userId]
                                        
                                        // ดึงข้อมูลการบูสต์
                                        val boostDoc = boostTasks[snapshot.documents.indexOf(document)].result
                                        if (boostDoc.exists()) {
                                            val boostCount = boostDoc.getLong("boostCount")?.toInt() ?: 0
                                            val boostedUsers = when (val users = boostDoc.get("boostedUsers")) {
                                                is List<*> -> users.filterIsInstance<String>()
                                                else -> emptyList()
                                            }
                                            this.boostCount = boostCount
                                            this.isBoosted = currentUserId != null && boostedUsers.contains(currentUserId)
                                        } else {
                                            // ถ้าไม่มีข้อมูลการบูสต์ ให้สร้างใหม่
                                            val initialData = hashMapOf(
                                                "boostCount" to 0,
                                                "boostedUsers" to listOf<String>()
                                            )
                                            firestore.collection("postBoosts").document(document.id)
                                                .set(initialData)
                                                .addOnSuccessListener {
                                                    Log.d("CustomerMainActivity", "Created new boost document for post ${document.id}")
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("CustomerMainActivity", "Error creating boost document: ${e.message}")
                                                }
                                            this.boostCount = 0
                                            this.isBoosted = false
                                        }
                                    }
                                    post?.let { fetchedPosts.add(it) }
                                }

                                // โหลดข้อมูลไลค์
                                if (currentUserId != null) {
                                    firestore.collection("likes")
                                        .whereEqualTo("userId", currentUserId)
                                        .get()
                                        .addOnSuccessListener { likeDocuments ->
                                            val likedPostIds = likeDocuments.map { it.getString("postId") }.toSet()
                                            for (post in fetchedPosts) {
                                                post.isLiked = post.postId != null && likedPostIds.contains(post.postId)
                                            }
                                            
                                            // เรียงลำดับและอัพเดท UI
                                            updatePostsList(fetchedPosts, paginate = false)
                                        }
                                } else {
                                    // อัพเดท UI โดยไม่มีข้อมูลไลค์
                                    updatePostsList(fetchedPosts, paginate = false)
                                }
                            }
                        }
                } else {
                    postList.clear()
                    feedAdapter.submitList(postList.toList())
                    showEmptyState()
                }
            } else {
                // ถ้าไม่มีโพสต์ ให้แสดงสถานะว่าง
                postList.clear()
                feedAdapter.submitList(postList.toList())
                showEmptyState()
            }
        }

        // Listener สำหรับการเปลี่ยนแปลงการบูสต์
        val boostListener = firestore.collection("postBoosts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("CustomerMainActivity", "Boost listener failed.", e)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val postId = change.document.id
                    val post = postList.find { it.postId == postId }
                    
                    if (post != null) {
                        val boostCount = change.document.getLong("boostCount")?.toInt() ?: 0
                        val boostedUsers = when (val users = change.document.get("boostedUsers")) {
                            is List<*> -> users.filterIsInstance<String>()
                            else -> emptyList()
                        }
                        
                        // อัพเดทข้อมูลการบูสต์
                        post.boostCount = boostCount
                        post.isBoosted = currentUserId != null && boostedUsers.contains(currentUserId)
                        
                        // อัพเดท UI
                        val position = postList.indexOf(post)
                        if (position != -1) {
                            feedAdapter.notifyItemChanged(position)
                        }
                    }
                }
            }

        // เก็บ listeners ไว้เพื่อยกเลิกเมื่อไม่ใช้งาน
        listeners.add(postsListener)
        listeners.add(boostListener)
    }

    private fun updatePostsList(posts: List<Post>, paginate: Boolean) {
        // ซ่อน loading indicator
        feedAdapter.setLoading(false)
        
        if (paginate) {
            postList.addAll(posts)
        } else {
            postList.clear()
            postList.addAll(posts)
        }
        
        feedAdapter.submitList(postList.toList())
        isLoading = false
        swipeRefreshLayout.isRefreshing = false
        showContent()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ยกเลิก listeners เมื่อ Activity ถูกทำลาย
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun refreshFeed() {
        Log.d("CustomerMainActivity", "Refreshing feed...")
        isLastPage = false
        currentPage = 0
        
        // Reset realtime listener
        resetRealtimeListener()
        
        // Fetch posts with current filter
        val filter = when {
            chipNearMe.isChecked -> "nearMe"
            else -> "all"
        }
        
        fetchPostsOptimized(filter, paginate = false, provinceFilter = currentProvince)
    }

    private fun loadMorePosts() {
        if (isLoading || isLastPage) return
        
        // ตรวจสอบว่ามีโพสต์อยู่แล้วหรือไม่
        if (postList.isEmpty()) {
            // ถ้าไม่มีโพสต์ ให้โหลดใหม่แทนการ paginate
            fetchPostsOptimized(filter = "all", paginate = false)
            return
        }
        
        // ตรวจสอบการเชื่อมต่ออินเทอร์เน็ต
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
            return
        }
        
        // แสดง loading indicator
        feedAdapter.setLoading(true)
        
        fetchPostsOptimized(filter = "all", paginate = true)
    }

    private fun toggleLike(post: Post) {
        val postId = post.postId ?: return
        val userId = currentUserId
        if (userId == null) {
            Toast.makeText(this, "กรุณาเข้าสู่ระบบเพื่อกดไลก์", Toast.LENGTH_SHORT).show()
            return
        }

        if (processingLikePostIds.contains(postId)) return
        processingLikePostIds.add(postId)

        val postIndex = postList.indexOfFirst { it.postId == postId }
        if (postIndex != -1) {
            feedAdapter.notifyItemChanged(postIndex)
        }

        val postRef = firestore.collection("posts").document(postId)
        val likeRef = firestore.collection("likes").document("${postId}_${userId}")
        val isCurrentlyLiked = post.isLiked

        val batch = firestore.batch()
        if (isCurrentlyLiked) {
            // Unlike
            batch.update(postRef, "likes", FieldValue.arrayRemove(userId))
            batch.update(postRef, "likeCount", FieldValue.increment(-1))
            batch.delete(likeRef)
        } else {
            // Like
            batch.update(postRef, "likes", FieldValue.arrayUnion(userId))
            batch.update(postRef, "likeCount", FieldValue.increment(1))
            batch.set(likeRef, mapOf(
                "postId" to postId,
                "userId" to userId,
                "timestamp" to Timestamp.now()
            ))
        }

        batch.commit().addOnCompleteListener {
            processingLikePostIds.remove(postId)
            // After the batch commit, we rely on the realtime listener to update the UI.
            // For a faster perceived update, we can fetch the single post and update it.
            postRef.get().addOnSuccessListener { updatedDoc ->
                if (updatedDoc.exists()) {
                    val finalIndex = postList.indexOfFirst { it.postId == postId }
                    if (finalIndex != -1) {
                        val postToUpdate = postList[finalIndex]
                        postToUpdate.likeCount = updatedDoc.getLong("likeCount")?.toInt() ?: postToUpdate.likeCount
                        when (val likes = updatedDoc.get("likes")) {
                            is List<*> -> {
                                postToUpdate.isLiked = likes.filterIsInstance<String>().contains(userId)
                            }
                        }
                        feedAdapter.notifyItemChanged(finalIndex)
                    }
                }
            }
            if (it.isSuccessful && !isCurrentlyLiked) {
                sendNotification(post.userId, "like", post.postId)
            }
        }
    }

    private fun sendNotification(recipientId: String?, type: String, postId: String?) {
        if (recipientId == null || currentUserId == null || currentUserId == recipientId) {
            return // Don't send notification to self or if recipient/sender is null
        }

        // Fetch sender's nickname and avatarId
        firestore.collection("users").document(currentUserId!!)
            .get()
            .addOnSuccessListener { senderDoc ->
                val senderName = senderDoc.getString("nickname") ?: senderDoc.getString("name") ?: "ไม่พบชื่อ"
                val senderAvatarId = senderDoc.getLong("avatarId")?.toInt() ?: 0

                val notificationMessage = when (type) {
                    "like" -> "$senderName ได้กดถูกใจโพสต์ของคุณ"
                    "comment" -> "$senderName ได้แสดงความคิดเห็นในโพสต์ของคุณ"
                    "follow" -> "$senderName ได้ติดตามคุณ"
                    else -> "มีการแจ้งเตือนใหม่"
                }

                val notification = Notification(
                    recipientId = recipientId,
                    senderId = currentUserId!!,
                    senderName = senderName,
                    senderAvatarId = senderAvatarId,
                    type = type,
                    message = notificationMessage,
                    postId = postId,
                    timestamp = Timestamp.now(),
                    read = false
                )

                firestore.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener { documentReference ->
                        Log.d("CustomerMainActivity", "Notification sent successfully: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("CustomerMainActivity", "Error sending notification: $e")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error fetching sender details for notification: $e")
            }
    }

    private fun checkPostLimit(onResult: (canPost: Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(false)
        val today = java.text.SimpleDateFormat("yyyyMMdd").format(java.util.Date())
        firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                val todayCount = docs.count { doc ->
                    val tsObj = doc.get("postTime")
                    val ts: java.util.Date? = when (tsObj) {
                        is com.google.firebase.Timestamp -> tsObj.toDate()
                        is Long -> java.util.Date(tsObj)
                        is String -> try { java.util.Date(tsObj.toLong()) } catch (e: Exception) { null }
                        else -> null
                    }
                    ts != null && java.text.SimpleDateFormat("yyyyMMdd").format(ts) == today
                }
                // จำกัด 5 โพสต์/วัน สำหรับผู้ใช้ทั่วไป
                checkPremiumStatus { isPremium ->
                    if (isPremium) onResult(true)
                    else onResult(todayCount < 5)
                }

            }
            .addOnFailureListener { onResult(false) }
    }

    private fun deletePost(post: Post) {
        val postId = post.postId ?: return

        firestore.collection("posts").document(postId)
            .delete()
            .addOnSuccessListener {
                // Delete associated likes
                firestore.collection("likes")
                    .whereEqualTo("postId", postId)
                    .get()
                    .addOnSuccessListener { likesSnapshot ->
                        val batch = firestore.batch()
                        likesSnapshot.documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                    }

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
                val newList = postList.toMutableList()
                newList.remove(post)
                postList.clear()
                postList.addAll(newList)
                feedAdapter.submitList(postList.toList())
                Toast.makeText(this, "ลบโพสต์เรียบร้อย", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error deleting post: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาดในการลบโพสต์: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        recyclerViewFeed.visibility = View.GONE
        emptyFeedTextView.visibility = View.GONE
        
        // ลบ click listener ออกจาก emptyFeedTextView เมื่อกำลังโหลด
        emptyFeedTextView.setOnClickListener(null)
    }

    private fun showEmptyState(message: String? = null) {
        loadingIndicator.visibility = View.GONE
        recyclerViewFeed.visibility = View.GONE
        emptyFeedTextView.text = message ?: getString(R.string.feed_empty)
        emptyFeedTextView.visibility = View.VISIBLE
        
        // เพิ่มปุ่มลองใหม่สำหรับ empty state
        emptyFeedTextView.setOnClickListener {
            refreshFeed()
        }
    }

    private fun showContent() {
        loadingIndicator.visibility = View.GONE
        recyclerViewFeed.visibility = View.VISIBLE
        emptyFeedTextView.visibility = View.GONE
        
        // ลบ click listener ออกจาก emptyFeedTextView เมื่อแสดงเนื้อหา
        emptyFeedTextView.setOnClickListener(null)
    }

    private fun showError(message: String) {
        try {
            loadingIndicator.visibility = View.GONE
            emptyFeedTextView.visibility = View.VISIBLE
            recyclerViewFeed.visibility = View.GONE
            emptyFeedTextView.text = message
            
            // เพิ่มปุ่มลองใหม่
            emptyFeedTextView.setOnClickListener {
                refreshFeed()
            }
        } catch (e: Exception) {
            Log.e("CustomerMainActivity", "Error showing error state: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                return networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e("CustomerMainActivity", "Error checking network: ${e.message}")
            return false
        }
    }

    private fun checkPremiumStatus(onResult: (isPremium: Boolean) -> Unit) {
        lifecycleScope.launch {
            val isPremium = PremiumChecker.isPremiumUser()
            onResult(isPremium)
        }
    }
    private fun resetToAllFilter(message: String) {
        showContent()
        chipAll.isChecked = true
        chipNearMe.isChecked = false
        
        // โหลดโพสต์ทั้งหมดใหม่
        fetchPostsOptimized(filter = "all", paginate = false)
    }

    private fun fetchCurrentProvince() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true

        if (!isGpsEnabled && !isNetworkEnabled) {
            AlertDialog.Builder(this)
                .setTitle("กรุณาเปิด Location Service")
                .setMessage("ฟีเจอร์ 'ใกล้ฉัน' ต้องการให้คุณเปิด GPS หรือ Location Service ในอุปกรณ์ของคุณ")
                .setPositiveButton("ไปที่การตั้งค่า") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("ยกเลิก", null)
                .show()
            resetToAllFilter("กรุณาเปิด Location Service ก่อนใช้งานฟีเจอร์นี้")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                getProvinceFromLocation(location)
            } else {
                // เพิ่ม timeout สำหรับการรอตำแหน่ง
                handler.postDelayed({
                    if (currentProvince == null) {
                        AlertDialog.Builder(this)
                            .setTitle("ไม่พบตำแหน่งล่าสุด")
                            .setMessage("ไม่สามารถดึงตำแหน่งปัจจุบันได้ กรุณารอสักครู่หรือขยับอุปกรณ์ แล้วลองใหม่อีกครั้ง")
                            .setPositiveButton("ลองใหม่") { _, _ ->
                                fetchCurrentProvince()
                            }
                            .setNegativeButton("ยกเลิก", null)
                            .show()
                        resetToAllFilter("ไม่สามารถดึงตำแหน่งปัจจุบันได้ กรุณาเปิด GPS และลองอีกครั้ง")
                    }
                }, 10000) // รอ 10 วินาที
            }
        }.addOnFailureListener { e ->
            Log.e("CustomerMainActivity", "Error getting location: ${e.message}")
            resetToAllFilter("เกิดข้อผิดพลาดในการดึงตำแหน่ง")
        }
    }

    private fun getProvinceFromLocation(location: Location) {
        showLoading()
        
        // เพิ่ม timeout สำหรับการแปลงตำแหน่ง
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (currentProvince == null) {
                resetToAllFilter("การแปลงตำแหน่งใช้เวลานานเกินไป กรุณาลองใหม่อีกครั้ง")
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000) // รอ 15 วินาที
        
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            handleProvinceResult(addresses)
                        }
                        override fun onError(errorMessage: String?) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            resetToAllFilter("เกิดข้อผิดพลาดในการแปลงตำแหน่งเป็นจังหวัด")
                        }
                    }
                )
            } else {
                // Deprecated version needs to be run in background
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        withContext(Dispatchers.Main) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            handleProvinceResult(addresses)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            resetToAllFilter("เกิดข้อผิดพลาดในการแปลงตำแหน่งเป็นจังหวัด")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // This initial catch is for Geocoder constructor, etc.
            timeoutHandler.removeCallbacks(timeoutRunnable)
            resetToAllFilter("เกิดข้อผิดพลาดในการเตรียมแปลงตำแหน่ง")
        }
    }

    private fun handleProvinceResult(addresses: List<Address>?) {
        runOnUiThread {
            if (!addresses.isNullOrEmpty()) {
                val rawProvince = addresses[0].adminArea
                Log.d("CustomerMainActivity", "Raw detected province: $rawProvince")
                
                // แปลงชื่อจังหวัดให้เป็นรูปแบบมาตรฐาน
                val normalizedProvince = normalizeProvinceName(rawProvince)
                Log.d("CustomerMainActivity", "Normalized province: $normalizedProvince")
                
                if (!normalizedProvince.isNullOrEmpty()) {
                    currentProvince = normalizedProvince
                    Log.d("CustomerMainActivity", "Set currentProvince to: $currentProvince")
                    
                    // รีเซ็ต realtime listener เพื่อใช้ province filter ใหม่
                    resetRealtimeListener()
                    
                    // โหลดโพสต์ใหม่
                    fetchPostsOptimized(filter = "all", paginate = false, provinceFilter = normalizedProvince)
                    Toast.makeText(this, "แสดงฟีดในจังหวัด: $normalizedProvince", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("CustomerMainActivity", "Normalized province is null or empty")
                    resetToAllFilter("ไม่พบข้อมูลจังหวัดจากตำแหน่งปัจจุบัน")
                }
            } else {
                Log.e("CustomerMainActivity", "No addresses found")
                resetToAllFilter("ไม่สามารถหาข้อมูลที่อยู่จากตำแหน่งปัจจุบันได้")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentProvince()
            } else {
                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                if (!showRationale) {
                    // ผู้ใช้กด Don't ask again
                    AlertDialog.Builder(this)
                        .setTitle("ต้องการสิทธิ์ Location")
                        .setMessage("ฟีเจอร์ 'ใกล้ฉัน' ต้องการสิทธิ์ Location กรุณาไปเปิดใน Settings")
                        .setPositiveButton("ไปที่การตั้งค่า") { _, _ ->
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            startActivity(intent)
                        }
                        .setNegativeButton("ยกเลิก", null)
                        .show()
                    resetToAllFilter("จำเป็นต้องได้รับอนุญาตเพื่อใช้ฟีเจอร์ 'ใกล้ฉัน'")
                } else {
                    resetToAllFilter("จำเป็นต้องได้รับอนุญาตเพื่อใช้ฟีเจอร์ 'ใกล้ฉัน'")
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_subscription -> {
                val intent = Intent(this, PremiumSubscriptionActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun resetRealtimeListener() {
        // ยกเลิก listeners เดิม
        listeners.forEach { it.remove() }
        listeners.clear()
        
        // สร้าง listeners ใหม่
        setupRealtimeListener()
    }

    private fun debugCheckPostsInFirebase() {
        Log.d("CustomerMainActivity", "=== DEBUG: Checking posts in Firebase ===")
        
        // ตรวจสอบโพสต์ทั้งหมด
        firestore.collection("posts")
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("CustomerMainActivity", "Found ${documents.size()} posts")
                documents.forEach { doc ->
                    val province = doc.getString("province")
                    val postText = doc.getString("postText")
                    Log.d("CustomerMainActivity", "Post ${doc.id}: province='$province', text='${postText?.take(50)}...'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking posts: ${e.message}")
            }
        
        // ตรวจสอบโพสต์จากจังหวัดสระบุรีโดยเฉพาะ
        firestore.collection("posts")
            .whereEqualTo("province", "สระบุรี")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("CustomerMainActivity", "Found ${documents.size()} posts from สระบุรี")
                documents.forEach { doc ->
                    val postText = doc.getString("postText")
                    Log.d("CustomerMainActivity", "สระบุรี post ${doc.id}: text='${postText?.take(50)}...'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking สระบุรี posts: ${e.message}")
            }
        
        // ตรวจสอบโพสต์จากจังหวัด Saraburi (ภาษาอังกฤษ)
        firestore.collection("posts")
            .whereEqualTo("province", "Saraburi")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("CustomerMainActivity", "Found ${documents.size()} posts from Saraburi")
                documents.forEach { doc ->
                    val postText = doc.getString("postText")
                    Log.d("CustomerMainActivity", "Saraburi post ${doc.id}: text='${postText?.take(50)}...'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking Saraburi posts: ${e.message}")
            }
        
        // ตรวจสอบโพสต์ที่ไม่มี province field
        firestore.collection("posts")
            .whereEqualTo("province", null)
            .limit(5)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("CustomerMainActivity", "Found ${documents.size()} posts with null province")
                documents.forEach { doc ->
                    val postText = doc.getString("postText")
                    Log.d("CustomerMainActivity", "Null province post ${doc.id}: text='${postText?.take(50)}...'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking null province posts: ${e.message}")
            }
        
        // ตรวจสอบโครงสร้างของโพสต์
        firestore.collection("posts")
            .limit(5)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("CustomerMainActivity", "Checking ${documents.size()} posts for province field structure")
                documents.forEach { doc ->
                    val province = doc.getString("province")
                    val hasProvinceField = doc.contains("province")
                    val postText = doc.getString("postText")
                    Log.d("CustomerMainActivity", "Post ${doc.id}: hasProvinceField=$hasProvinceField, province='$province', text='${postText?.take(50)}...'")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking posts structure: ${e.message}")
            }
    }

    private fun normalizeProvinceName(province: String?): String? {
        if (province.isNullOrEmpty()) return null
        
        // แปลงชื่อจังหวัดให้เป็นรูปแบบมาตรฐาน
        return when (province.lowercase()) {
            "saraburi", "สระบุรี" -> "สระบุรี"
            "bangkok", "กรุงเทพมหานคร", "กรุงเทพฯ", "กรุงเทพ" -> "กรุงเทพมหานคร"
            "nonthaburi", "นนทบุรี" -> "นนทบุรี"
            "pathum thani", "pathum thani", "ปทุมธานี" -> "ปทุมธานี"
            "samut prakan", "samut prakan", "สมุทรปราการ" -> "สมุทรปราการ"
            "samut sakhon", "samut sakhon", "สมุทรสาคร" -> "สมุทรสาคร"
            "nakhon pathom", "nakhon pathom", "นครปฐม" -> "นครปฐม"
            "ayutthaya", "พระนครศรีอยุธยา", "อยุธยา" -> "พระนครศรีอยุธยา"
            "ang thong", "ang thong", "อ่างทอง" -> "อ่างทอง"
            "lop buri", "lop buri", "ลพบุรี" -> "ลพบุรี"
            "sing buri", "sing buri", "สิงห์บุรี" -> "สิงห์บุรี"
            "chai nat", "chai nat", "ชัยนาท" -> "ชัยนาท"
            "suphan buri", "suphan buri", "สุพรรณบุรี" -> "สุพรรณบุรี"
            "kanchanaburi", "กาญจนบุรี" -> "กาญจนบุรี"
            "ratchaburi", "ราชบุรี" -> "ราชบุรี"
            "phetchaburi", "เพชรบุรี" -> "เพชรบุรี"
            "prachuap khiri khan", "prachuap khiri khan", "ประจวบคีรีขันธ์" -> "ประจวบคีรีขันธ์"
            "chumphon", "ชุมพร" -> "ชุมพร"
            "surat thani", "surat thani", "สุราษฎร์ธานี" -> "สุราษฎร์ธานี"
            "nakhon si thammarat", "nakhon si thammarat", "นครศรีธรรมราช" -> "นครศรีธรรมราช"
            "krabi", "กระบี่" -> "กระบี่"
            "phang nga", "phang nga", "พังงา" -> "พังงา"
            "phuket", "ภูเก็ต" -> "ภูเก็ต"
            "ranong", "ระนอง" -> "ระนอง"
            "trang", "ตรัง" -> "ตรัง"
            "satun", "สตูล" -> "สตูล"
            "songkhla", "สงขลา" -> "สงขลา"
            "pattani", "ปัตตานี" -> "ปัตตานี"
            "yala", "ยะลา" -> "ยะลา"
            "narathiwat", "นราธิวาส" -> "นราธิวาส"
            "chachoengsao", "ฉะเชิงเทรา" -> "ฉะเชิงเทรา"
            "chon buri", "chon buri", "ชลบุรี" -> "ชลบุรี"
            "rayong", "ระยอง" -> "ระยอง"
            "chanthaburi", "จันทบุรี" -> "จันทบุรี"
            "trat", "ตราด" -> "ตราด"
            "chaiyaphum", "ชัยภูมิ" -> "ชัยภูมิ"
            "nakhon ratchasima", "nakhon ratchasima", "นครราชสีมา" -> "นครราชสีมา"
            "buri ram", "buri ram", "บุรีรัมย์" -> "บุรีรัมย์"
            "surin", "สุรินทร์" -> "สุรินทร์"
            "si sa ket", "si sa ket", "ศรีสะเกษ" -> "ศรีสะเกษ"
            "ubon ratchathani", "ubon ratchathani", "อุบลราชธานี" -> "อุบลราชธานี"
            "yasothon", "ยโสธร" -> "ยโสธร"
            "amnat charoen", "amnat charoen", "อำนาจเจริญ" -> "อำนาจเจริญ"
            "mukdahan", "มุกดาหาร" -> "มุกดาหาร"
            "nong khai", "nong khai", "หนองคาย" -> "หนองคาย"
            "bueng kan", "bueng kan", "บึงกาฬ" -> "บึงกาฬ"
            "nong bua lam phu", "nong bua lam phu", "หนองบัวลำภู" -> "หนองบัวลำภู"
            "udon thani", "udon thani", "อุดรธานี" -> "อุดรธานี"
            "sakon nakhon", "sakon nakhon", "สกลนคร" -> "สกลนคร"
            "nakhon phanom", "nakhon phanom", "นครพนม" -> "นครพนม"
            "kalasin", "กาฬสินธุ์" -> "กาฬสินธุ์"
            "maha sarakham", "maha sarakham", "มหาสารคาม" -> "มหาสารคาม"
            "roi et", "roi et", "ร้อยเอ็ด" -> "ร้อยเอ็ด"
            "khon kaen", "khon kaen", "ขอนแก่น" -> "ขอนแก่น"
            "loei", "เลย" -> "เลย"
            "nong bua lamphu", "nong bua lamphu", "หนองบัวลำภู" -> "หนองบัวลำภู"
            "udonthani", "udonthani", "อุดรธานี" -> "อุดรธานี"
            "sakonnakhon", "sakonnakhon", "สกลนคร" -> "สกลนคร"
            "nakhonphanom", "nakhonphanom", "นครพนม" -> "นครพนม"
            "kalasin", "กาฬสินธุ์" -> "กาฬสินธุ์"
            "mahasarakham", "mahasarakham", "มหาสารคาม" -> "มหาสารคาม"
            "roiet", "roiet", "ร้อยเอ็ด" -> "ร้อยเอ็ด"
            "khonkaen", "khonkaen", "ขอนแก่น" -> "ขอนแก่น"
            "phitsanulok", "พิษณุโลก" -> "พิษณุโลก"
            "phichit", "พิจิตร" -> "พิจิตร"
            "phetchabun", "เพชรบูรณ์" -> "เพชรบูรณ์"
            "kamphaeng phet", "kamphaeng phet", "กำแพงเพชร" -> "กำแพงเพชร"
            "tak", "ตาก" -> "ตาก"
            "sukhothai", "สุโขทัย" -> "สุโขทัย"
            "uttaradit", "อุตรดิตถ์" -> "อุตรดิตถ์"
            "phrae", "แพร่" -> "แพร่"
            "nan", "น่าน" -> "น่าน"
            "lampang", "ลำปาง" -> "ลำปาง"
            "lamphun", "ลำพูน" -> "ลำพูน"
            "chiang mai", "chiang mai", "เชียงใหม่" -> "เชียงใหม่"
            "chiang rai", "chiang rai", "เชียงราย" -> "เชียงราย"
            "phayao", "พะเยา" -> "พะเยา"
            "mae hong son", "mae hong son", "แม่ฮ่องสอน" -> "แม่ฮ่องสอน"
            "nakhon sawan", "nakhon sawan", "นครสวรรค์" -> "นครสวรรค์"
            "uthai thani", "uthai thani", "อุทัยธานี" -> "อุทัยธานี"
            "chainat", "chainat", "ชัยนาท" -> "ชัยนาท"
            "suphanburi", "suphanburi", "สุพรรณบุรี" -> "สุพรรณบุรี"
            "kanchanaburi", "กาญจนบุรี" -> "กาญจนบุรี"
            "ratchaburi", "ราชบุรี" -> "ราชบุรี"
            "phetchaburi", "เพชรบุรี" -> "เพชรบุรี"
            "prachuapkhirikhan", "prachuapkhirikhan", "ประจวบคีรีขันธ์" -> "ประจวบคีรีขันธ์"
            "chumphon", "ชุมพร" -> "ชุมพร"
            "suratthani", "suratthani", "สุราษฎร์ธานี" -> "สุราษฎร์ธานี"
            "nakhonsithammarat", "nakhonsithammarat", "นครศรีธรรมราช" -> "นครศรีธรรมราช"
            "krabi", "กระบี่" -> "กระบี่"
            "phangnga", "phangnga", "พังงา" -> "พังงา"
            "phuket", "ภูเก็ต" -> "ภูเก็ต"
            "ranong", "ระนอง" -> "ระนอง"
            "trang", "ตรัง" -> "ตรัง"
            "satun", "สตูล" -> "สตูล"
            "songkhla", "สงขลา" -> "สงขลา"
            "pattani", "ปัตตานี" -> "ปัตตานี"
            "yala", "ยะลา" -> "ยะลา"
            "narathiwat", "นราธิวาส" -> "นราธิวาส"
            "chachoengsao", "ฉะเชิงเทรา" -> "ฉะเชิงเทรา"
            "chonburi", "chonburi", "ชลบุรี" -> "ชลบุรี"
            "rayong", "ระยอง" -> "ระยอง"
            "chanthaburi", "จันทบุรี" -> "จันทบุรี"
            "trat", "ตราด" -> "ตราด"
            "chaiyaphum", "ชัยภูมิ" -> "ชัยภูมิ"
            "nakhonratchasima", "nakhonratchasima", "นครราชสีมา" -> "นครราชสีมา"
            "buriram", "buriram", "บุรีรัมย์" -> "บุรีรัมย์"
            "surin", "สุรินทร์" -> "สุรินทร์"
            "sisaket", "sisaket", "ศรีสะเกษ" -> "ศรีสะเกษ"
            "ubonratchathani", "ubonratchathani", "อุบลราชธานี" -> "อุบลราชธานี"
            "yasothon", "ยโสธร" -> "ยโสธร"
            "amnatcharoen", "amnatcharoen", "อำนาจเจริญ" -> "อำนาจเจริญ"
            "mukdahan", "มุกดาหาร" -> "มุกดาหาร"
            "nongkhai", "nongkhai", "หนองคาย" -> "หนองคาย"
            "buengkan", "buengkan", "บึงกาฬ" -> "บึงกาฬ"
            "nongbualamphu", "nongbualamphu", "หนองบัวลำภู" -> "หนองบัวลำภู"
            "udonthani", "udonthani", "อุดรธานี" -> "อุดรธานี"
            "sakonnakhon", "sakonnakhon", "สกลนคร" -> "สกลนคร"
            "nakhonphanom", "nakhonphanom", "นครพนม" -> "นครพนม"
            "kalasin", "กาฬสินธุ์" -> "กาฬสินธุ์"
            "mahasarakham", "mahasarakham", "มหาสารคาม" -> "มหาสารคาม"
            "roiet", "roiet", "ร้อยเอ็ด" -> "ร้อยเอ็ด"
            "khonkaen", "khonkaen", "ขอนแก่น" -> "ขอนแก่น"
            "loei", "เลย" -> "เลย"
            "phitsanulok", "พิษณุโลก" -> "พิษณุโลก"
            "phichit", "พิจิตร" -> "พิจิตร"
            "phetchabun", "เพชรบูรณ์" -> "เพชรบูรณ์"
            "kamphaengphet", "kamphaengphet", "กำแพงเพชร" -> "กำแพงเพชร"
            "tak", "ตาก" -> "ตาก"
            "sukhothai", "สุโขทัย" -> "สุโขทัย"
            "uttaradit", "อุตรดิตถ์" -> "อุตรดิตถ์"
            "phrae", "แพร่" -> "แพร่"
            "nan", "น่าน" -> "น่าน"
            "lampang", "ลำปาง" -> "ลำปาง"
            "lamphun", "ลำพูน" -> "ลำพูน"
            "chiangmai", "chiangmai", "เชียงใหม่" -> "เชียงใหม่"
            "chiangrai", "chiangrai", "เชียงราย" -> "เชียงราย"
            "phayao", "พะเยา" -> "พะเยา"
            "maehongson", "maehongson", "แม่ฮ่องสอน" -> "แม่ฮ่องสอน"
            "nakhonsawan", "nakhonsawan", "นครสวรรค์" -> "นครสวรรค์"
            "uthaithani", "uthaithani", "อุทัยธานี" -> "อุทัยธานี"
            else -> province // ถ้าไม่ตรงกับที่กำหนดไว้ ให้ใช้ชื่อเดิม
        }
    }

    private fun checkIfAnyPostsHaveProvinceField(targetProvince: String) {
        // ตรวจสอบว่ามีโพสต์ที่มี province field หรือไม่
        firestore.collection("posts")
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val postsWithProvince = documents.filter { doc -> doc.contains("province") }
                val postsWithoutProvince = documents.filter { doc -> !doc.contains("province") }
                
                Log.d("CustomerMainActivity", "Found ${postsWithProvince.size} posts with province field")
                Log.d("CustomerMainActivity", "Found ${postsWithoutProvince.size} posts without province field")
                
                if (postsWithProvince.isEmpty()) {
                    // ถ้าไม่มีโพสต์ที่มี province field เลย แสดงข้อความแจ้งเตือน
                    showEmptyState("โพสต์เก่าไม่มีข้อมูลจังหวัด\nกรุณาลองโพสต์ใหม่เพื่อใช้ฟีเจอร์ 'ใกล้ฉัน'")
                    Toast.makeText(this, "โพสต์เก่าไม่มีข้อมูลจังหวัด กรุณาลองโพสต์ใหม่", Toast.LENGTH_LONG).show()
                } else {
                    // ถ้ามีโพสต์ที่มี province field แต่ไม่มีโพสต์จากจังหวัดที่ต้องการ
                    showEmptyState("ยังไม่มีโพสต์ใดๆ ในจังหวัด $targetProvince")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error checking posts structure: ${e.message}")
                showEmptyState("ยังไม่มีโพสต์ใดๆ ในจังหวัด $targetProvince")
            }
    }

    // Optimized Firebase queries with batch operations
    private fun fetchPostsOptimized(filter: String = "all", paginate: Boolean = false, retryCount: Int = 0, provinceFilter: String? = null) {
        if (isLoading && !paginate) return
        isLoading = true
        
        try {
            val query = when (filter) {
                "nearMe" -> {
                    if (provinceFilter != null) {
                        firestore.collection("posts")
                            .whereEqualTo("province", provinceFilter)
                            .orderBy("postTime", Query.Direction.DESCENDING)
                            .limit(PAGE_SIZE.toLong())
                    } else {
                        firestore.collection("posts")
                            .orderBy("postTime", Query.Direction.DESCENDING)
                            .limit(PAGE_SIZE.toLong())
                    }
                }
                else -> {
                    firestore.collection("posts")
                        .orderBy("postTime", Query.Direction.DESCENDING)
                        .limit(PAGE_SIZE.toLong())
                }
            }

            query.get()
                .addOnSuccessListener { snapshot ->
                    try {
                        val fetchedPosts = mutableListOf<Post>()
                        
                        // Parse posts
                        for (document in snapshot.documents) {
                            try {
                                val post = document.toObject(Post::class.java)?.apply {
                                    postId = document.id
                                }
                                post?.let { fetchedPosts.add(it) }
                            } catch (e: Exception) {
                                Log.e("CustomerMainActivity", "Error parsing post: ${e.message}")
                            }
                        }

                        // Batch load all related data
                        loadRelatedDataBatch(fetchedPosts, paginate, provinceFilter)
                        
                    } catch (e: Exception) {
                        Log.e("CustomerMainActivity", "Error processing posts: ${e.message}")
                        handleFetchError(e, "Error processing posts", paginate)
                    }
                }
                .addOnFailureListener { e ->
                    if (retryCount < 3) {
                        Log.w("CustomerMainActivity", "Retrying fetch posts (attempt ${retryCount + 1})")
                        handler.postDelayed({
                            fetchPostsOptimized(filter = filter, paginate = paginate, retryCount = retryCount + 1, provinceFilter = provinceFilter)
                        }, 1000)
                    } else {
                        handleFetchError(e, "Error fetching posts", paginate)
                    }
                }
        } catch (e: Exception) {
            handleFetchError(e, "Error in fetchPostsOptimized", paginate)
        }
    }

    // Overloaded function without parameters
    // private fun fetchPostsOptimized() {
    //     fetchPostsOptimized(filter = "all", paginate = false)
    // }

    private fun loadRelatedDataBatch(posts: List<Post>, paginate: Boolean, provinceFilter: String?) {
        if (posts.isEmpty()) {
            updatePostsList(posts, paginate)
            return
        }

        val postIds = posts.mapNotNull { it.postId }
        val userIds = posts.mapNotNull { it.userId }.distinct()
        
        // Create batch queries
        val batchTasks = mutableListOf<Task<*>>()
        
        // Batch load boost data
        if (postIds.isNotEmpty()) {
            val boostQuery = firestore.collection("postBoosts")
                .whereIn(FieldPath.documentId(), postIds)
            batchTasks.add(boostQuery.get())
        }
        
        // Batch load like data
        if (currentUserId != null) {
            val likeQuery = firestore.collection("likes")
                .whereEqualTo("userId", currentUserId)
                .whereIn("postId", postIds)
            batchTasks.add(likeQuery.get())
        }
        
        // Batch load user data
        if (userIds.isNotEmpty()) {
            val userQuery = firestore.collection("users")
                .whereIn(FieldPath.documentId(), userIds)
            batchTasks.add(userQuery.get())
        }
        
        // Execute all batch queries
        if (batchTasks.isEmpty()) {
            updatePostsList(posts, paginate)
        } else {
            Tasks.whenAllComplete(batchTasks).addOnSuccessListener {
                processBatchData(posts, batchTasks, paginate)
            }.addOnFailureListener { e ->
                Log.e("CustomerMainActivity", "Error in batch queries: ${e.message}")
                updatePostsList(posts, paginate)
            }
        }
    }

    private fun processBatchData(posts: List<Post>, tasks: List<Task<*>>, paginate: Boolean) {
        try {
            // Process boost data
            if (tasks.isNotEmpty() && tasks[0].isSuccessful) {
                val boostSnapshot = tasks[0].result as QuerySnapshot
                val boostMap = boostSnapshot.documents.associate { 
                    it.id to (it.getLong("boostCount")?.toInt() ?: 0) 
                }
                
                posts.forEach { post ->
                    post.boostCount = boostMap[post.postId] ?: 0
                }
            }
            
            // Process like data
            if (tasks.size > 1 && tasks[1].isSuccessful) {
                val likeSnapshot = tasks[1].result as QuerySnapshot
                val likedPostIds = likeSnapshot.documents.map { it.getString("postId") }.toSet()
                
                posts.forEach { post ->
                    post.isLiked = post.postId != null && likedPostIds.contains(post.postId)
                }
            }
            
            // Process user data
            if (tasks.size > 2 && tasks[2].isSuccessful) {
                val userSnapshot = tasks[2].result as QuerySnapshot
                val userMap = userSnapshot.documents.associate { 
                    it.id to (it.getString("nickname") ?: it.getString("displayName") ?: "ไม่พบชื่อ") 
                }
                
                posts.forEach { post ->
                    post.nickname = userMap[post.userId]
                }
            }
            
            // Sort and update
            val sortedPosts = posts.sortedWith(
                compareByDescending<Post> { it.isBoosted }
                    .thenByDescending { it.boostCount }
                    .thenByDescending { it.postTime }
            )
            
            updatePostsList(sortedPosts, paginate)
            
        } catch (e: Exception) {
            Log.e("CustomerMainActivity", "Error processing batch data: ${e.message}")
            updatePostsList(posts, paginate)
        }
    }

    private fun handleFetchError(e: Exception, errorMessage: String, paginate: Boolean) {
        Log.e("CustomerMainActivity", "$errorMessage: ${e.message}")
        isLoading = false
        swipeRefreshLayout.isRefreshing = false
        feedAdapter.setLoading(false)
        
        if (!paginate && postList.isEmpty()) {
            showEmptyState("เกิดข้อผิดพลาดในการโหลดข้อมูล\nกรุณาลองใหม่อีกครั้ง")
        } else if (paginate) {
            // สำหรับ pagination error ให้แสดง toast เท่านั้น
            Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลเพิ่มเติม", Toast.LENGTH_SHORT).show()
        } else {
            showError("เกิดข้อผิดพลาดในการโหลดข้อมูล\nกรุณาลองใหม่อีกครั้ง")
        }
    }
}





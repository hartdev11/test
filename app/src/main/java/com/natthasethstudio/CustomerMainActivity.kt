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
    private val PAGE_SIZE = 10
    private var totalCount = 0
    private lateinit var loadingIndicator: ProgressBar

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
            fetchPosts()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    private lateinit var binding: ActivityCustomerMainBinding
    private var addPhotoButton: View? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentProvince: String? = null
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
            showLoading()
            fetchPosts()
        }
        chipNearMe?.setOnClickListener {
            chipAll.isChecked = false
            chipNearMe.isChecked = true
            showLoading()
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
                if (isLoading || isLastPage) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // ตรวจสอบว่าถึงจุดที่ควรโหลดเพิ่มหรือไม่
                val isNearEnd = (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                if (isNearEnd && totalCount >= PAGE_SIZE) {
                    loadMorePosts()
                }
            }
        })

        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyFeedTextView = findViewById(R.id.emptyFeedTextView)

        // Fetch initial posts
        fetchPosts(paginate = false)
        setupRealtimeListener()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
        // Refresh feed on start to get latest data
        refreshFeed()
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
        // Listener สำหรับโพสต์
        val postsListener = firestore.collection("posts")
            .orderBy("postTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("CustomerMainActivity", "Posts listener failed.", e)
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
                                                updatePostsList(fetchedPosts)
                                            }
                                    } else {
                                        // อัพเดท UI โดยไม่มีข้อมูลไลค์
                                        updatePostsList(fetchedPosts)
                                    }
                                }
                            }
                    } else {
                        postList.clear()
                        feedAdapter.submitList(postList.toList())
                    }
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

    private fun updatePostsList(posts: List<Post>) {
        // เรียงลำดับโพสต์
        val sortedPosts = posts.sortedWith(
            compareByDescending<Post> { it.isBoosted }
                .thenByDescending { it.boostCount }
                .thenByDescending { it.postTime }
        )
        
        // อัพเดท UI
        postList.clear()
        postList.addAll(sortedPosts)
        feedAdapter.submitList(postList.toList())
    }

    override fun onDestroy() {
        super.onDestroy()
        // ยกเลิก listeners เมื่อ Activity ถูกทำลาย
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun refreshFeed() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        // Clear adapter caches before refresh
        feedAdapter.clearAllCaches()
        fetchPosts(paginate = false)
    }

    private fun fetchPosts(filter: String? = null, paginate: Boolean = true, retryCount: Int = 0, provinceFilter: String? = null) {
        try {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
                loadingIndicator.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                return
            }

            if (!paginate) {
                showLoading()
                postList.clear()
                feedAdapter.notifyDataSetChanged()
            }

            isLoading = true
            var query = firestore.collection("posts")
                .orderBy("postTime", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            when (filter) {
                "photo", "video", "article" -> query = query.whereEqualTo("type", filter)
                else -> {} // ไม่ต้องกรองอะไรเพิ่มเติม
            }

            if (provinceFilter != null) {
                query = query.whereEqualTo("province", provinceFilter)
            }

            if (paginate && lastVisiblePost != null) {
                query = query.startAfter(lastVisiblePost!!)
            }

            query.get()
                .addOnSuccessListener { documents ->
                    try {
                        if (!paginate) {
                            postList.clear()
                        }

                        if (documents.isEmpty) {
                            isLastPage = true
                            if (postList.isEmpty()) {
                                if (provinceFilter != null) {
                                    showEmptyState("ยังไม่มีโพสต์ใดๆ ในจังหวัด $provinceFilter")
                                } else {
                                    showEmptyState() // Uses default message
                                }
                            }
                            isLoading = false
                            swipeRefreshLayout.isRefreshing = false
                            return@addOnSuccessListener
                        }

                        lastVisiblePost = documents.documents.lastOrNull()
                        val fetchedPosts = mutableListOf<Post>()
                        
                        documents.forEach { doc ->
                            try {
                                val post = doc.toObject(Post::class.java)
                                post?.let { 
                                    post.postId = doc.id
                                    fetchedPosts.add(post)
                                }
                            } catch (e: Exception) {
                                Log.e("CustomerMainActivity", "Error parsing post: ${e.message}")
                            }
                        }

                        // Load boost data for all posts
                        val boostTasks = fetchedPosts.mapNotNull { post ->
                            val postId = post.postId ?: return@mapNotNull null
                            firestore.collection("postBoosts").document(postId).get()
                        }

                        // Load like data
                        val likeTask = if (currentUserId != null) {
                            firestore.collection("likes")
                                .whereEqualTo("userId", currentUserId)
                                .get()
                        } else null

                        // Wait for all data to load before updating UI
                        val allTasks = mutableListOf<Task<*>>().apply {
                            addAll(boostTasks)
                            likeTask?.let { add(it) }
                        }

                        if (allTasks.isEmpty()) {
                            postList.addAll(fetchedPosts)
                            feedAdapter.submitList(postList.toList())
                            isLoading = false
                            swipeRefreshLayout.isRefreshing = false
                            showContent()
                        } else {
                            Tasks.whenAllComplete(allTasks).addOnSuccessListener {
                                // Process boost data
                                boostTasks.forEachIndexed { index, task ->
                                    try {
                                        val doc = task.result
                                        val post = fetchedPosts[index]
                                        val boostCount = doc.getLong("boostCount")?.toInt() ?: 0
                                        val boostedUsers = when (val users = doc.get("boostedUsers")) {
                                            is List<*> -> users.filterIsInstance<String>()
                                            else -> emptyList()
                                        }
                                        post.boostCount = boostCount
                                        post.isBoosted = currentUserId != null && boostedUsers.contains(currentUserId)
                                    } catch (e: Exception) {
                                        Log.e("CustomerMainActivity", "Error processing boost data: ${e.message}")
                                    }
                                }

                                // Process like data
                                likeTask?.let { task ->
                                    try {
                                        val likeDocuments = task.result
                                        val likedPostIds = likeDocuments.map { it.getString("postId") }.toSet()
                                        for (post in fetchedPosts) {
                                            post.isLiked = post.postId != null && likedPostIds.contains(post.postId)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CustomerMainActivity", "Error processing like data: ${e.message}")
                                    }
                                }

                                // Sort posts by boost status and count
                                fetchedPosts.sortWith(
                                    compareByDescending<Post> { it.isBoosted }
                                        .thenByDescending { it.boostCount }
                                        .thenByDescending { it.postTime }
                                )

                                postList.addAll(fetchedPosts)
                                feedAdapter.submitList(postList.toList())
                                isLoading = false
                                swipeRefreshLayout.isRefreshing = false
                                showContent()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CustomerMainActivity", "Error waiting for tasks: ${e.message}")
                                if (retryCount < 3) {
                                    handler.postDelayed({
                                        fetchPosts(filter, paginate, retryCount + 1, provinceFilter)
                                    }, 1000)
                                } else {
                                    postList.addAll(fetchedPosts)
                                    feedAdapter.submitList(postList.toList())
                                    isLoading = false
                                    swipeRefreshLayout.isRefreshing = false
                                    showContent()
                                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลบางส่วน", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CustomerMainActivity", "Error processing posts: ${e.message}")
                        handleFetchError(e, "Error processing posts", paginate)
                    }
                }
                .addOnFailureListener { e ->
                    if (retryCount < 3) {
                        Log.w("CustomerMainActivity", "Retrying fetch posts (attempt ${retryCount + 1})")
                        handler.postDelayed({
                            fetchPosts(filter, paginate, retryCount + 1, provinceFilter)
                        }, 1000)
                    } else {
                        handleFetchError(e, "Error fetching posts", paginate)
                    }
                }
        } catch (e: Exception) {
            handleFetchError(e, "Error in fetchPosts", paginate)
        }
    }

    private fun handleFetchError(e: Exception, errorMessage: String, paginate: Boolean) {
        Log.e("CustomerMainActivity", "$errorMessage: ${e.message}")
        isLoading = false
        swipeRefreshLayout.isRefreshing = false
        if (!paginate && postList.isEmpty()) {
            showEmptyState()
        }
        Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูล", Toast.LENGTH_SHORT).show()
    }

    private fun loadMorePosts() {
        if (isLoading || isLastPage) return
        fetchPosts(paginate = true)
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
    }

    private fun showEmptyState(message: String? = null) {
        loadingIndicator.visibility = View.GONE
        recyclerViewFeed.visibility = View.GONE
        emptyFeedTextView.text = message ?: getString(R.string.feed_empty)
        emptyFeedTextView.visibility = View.VISIBLE
    }

    private fun showContent() {
        loadingIndicator.visibility = View.GONE
        recyclerViewFeed.visibility = View.VISIBLE
        emptyFeedTextView.visibility = View.GONE
    }

    private fun showError(message: String) {
        try {
            loadingProgressBar.visibility = View.GONE
            emptyFeedTextView.visibility = View.VISIBLE
            recyclerViewFeed.visibility = View.GONE
            emptyFeedTextView.text = message
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

    private fun resetToAllFilter(errorMessage: String? = null) {
        if (errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
        showContent()
        chipAll.isChecked = true
        chipNearMe.isChecked = false
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
        }.addOnFailureListener {
            resetToAllFilter("เกิดข้อผิดพลาดในการดึงตำแหน่ง")
        }
    }

    private fun getProvinceFromLocation(location: Location) {
        showLoading()
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            handleProvinceResult(addresses)
                        }
                        override fun onError(errorMessage: String?) {
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
                            handleProvinceResult(addresses)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            resetToAllFilter("เกิดข้อผิดพลาดในการแปลงตำแหน่งเป็นจังหวัด")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // This initial catch is for Geocoder constructor, etc.
            resetToAllFilter("เกิดข้อผิดพลาดในการเตรียมแปลงตำแหน่ง")
        }
    }

    private fun handleProvinceResult(addresses: List<Address>?) {
        runOnUiThread {
            if (!addresses.isNullOrEmpty()) {
                val province = addresses[0].adminArea
                if (!province.isNullOrEmpty()) {
                    currentProvince = province
                    fetchPosts(provinceFilter = province)
                    Toast.makeText(this, "แสดงฟีดในจังหวัด: $province", Toast.LENGTH_SHORT).show()
                } else {
                    resetToAllFilter("ไม่พบข้อมูลจังหวัดจากตำแหน่งปัจจุบัน")
                }
            } else {
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
}




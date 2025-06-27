package com.natthasethstudio.sethpos

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.adapter.RewardAdapter
import com.natthasethstudio.sethpos.model.Reward
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class CheckInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var textCheckInStatus: TextView
    private lateinit var textCheckInTime: TextView
    private lateinit var textStreakCount: TextView
    private lateinit var textTotalCheckIns: TextView
    private lateinit var recyclerViewRewards: RecyclerView
    private lateinit var checkInButton: MaterialButton
    private lateinit var buttonRewardsInventory: MaterialButton
    private lateinit var toolbar: Toolbar
    private lateinit var rewardAdapter: RewardAdapter

    // Wheel of Fortune UI elements
    private lateinit var wheelOfFortuneCard: MaterialCardView
    private lateinit var wheelImageView: ImageView
    private lateinit var spinButton: MaterialButton
    private lateinit var remainingSpinsText: TextView
    private lateinit var watchAdButton: MaterialButton

    private var currentStreak = 0
    private var totalCheckIns = 0
    private var lastCheckInDate: Date? = null
    private var checkInListener: ListenerRegistration? = null
    private var claimedRewardsListener: ListenerRegistration? = null
    
    private var remainingSpins = 0
    private var rewardedAd: RewardedAd? = null
    private val AD_UNIT_ID = "ca-app-pub-6439629123336863/8095385897" // Rewarded Ad Unit ID for wheel
    private var dailyAdWatches = 0
    private val MAX_DAILY_AD_WATCHES = 3

    // New fields for specific rewards
    private var postPermissions = 0
    private var unlimitedPostsUntil: Long = 0L
    private var unlimitedNotificationsUntil: Long = 0L

    private val wheelColors = listOf(
        "Red", "Orange", "Yellow", "Green", "Blue", "Purple"
    )

    // Define reward mapping for each color section (clockwise from top)
    private val rewardMapping = mapOf(
        "Red" to listOf("POST_PERMISSION_1", "POST_PERMISSION_2"), // สิทธิ์ในการโพสต์รูปได้ 1 ครั้ง, สิทธิ์โพสต์รูปภาพได้ 2 ครั้ง
        "Green" to listOf("UNLIMITED_POSTS_1_DAY", "UNLIMITED_NOTIFICATIONS_1_DAY"), // โพสต์ได้ไม่จำกัด 1 วัน, ดูแจ้งเตือนได้ไม่จำกัด 1 วัน
        "Orange" to listOf("NO_REWARD"),
        "Yellow" to listOf("NO_REWARD"),
        "Blue" to listOf("NO_REWARD"),
        "Purple" to listOf("NO_REWARD")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Initialize views
        textCheckInStatus = findViewById(R.id.textCheckInStatus)
        textCheckInTime = findViewById(R.id.textCheckInTime)
        textStreakCount = findViewById(R.id.textStreakCount)
        textTotalCheckIns = findViewById(R.id.textTotalCheckIns)
        recyclerViewRewards = findViewById(R.id.recyclerViewRewards)
        checkInButton = findViewById(R.id.buttonCheckIn)
        buttonRewardsInventory = findViewById(R.id.buttonRewardsInventory)

        // Initialize Wheel of Fortune views
        wheelOfFortuneCard = findViewById(R.id.wheelOfFortuneCard)
        wheelImageView = findViewById(R.id.wheelImageView)
        spinButton = findViewById(R.id.spinButton)
        remainingSpinsText = findViewById(R.id.remainingSpinsText)
        watchAdButton = findViewById(R.id.watchAdButton)

        // Set up toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "เช็คอิน"
        }

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Set up RecyclerView for rewards
        rewardAdapter = RewardAdapter { reward ->
            if (reward.isAvailable && !reward.isClaimed) {
                claimReward(reward)
            } else if (reward.isClaimed) {
                showRewardDetails(reward)
            }
        }
        recyclerViewRewards.layoutManager = LinearLayoutManager(this)
        recyclerViewRewards.adapter = rewardAdapter

        // Set up check-in button with animation
        checkInButton.setOnClickListener {
            if (isNetworkAvailable()) {
                animateCheckInButton()
                performCheckIn()
            } else {
                Toast.makeText(this, "กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up rewards inventory button
        buttonRewardsInventory.setOnClickListener {
            if (isNetworkAvailable()) {
                try {
                    val intent = RewardsInventoryActivity.newIntent(this)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting RewardsInventoryActivity: ${e.message}")
                    Toast.makeText(this, "ไม่สามารถเปิดคลังรางวัลได้", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup spin button
        spinButton.setOnClickListener {
            if (remainingSpins > 0) {
                spinWheel()
            } else {
                Toast.makeText(this, "คุณไม่มีสิทธิ์หมุนวงล้อแล้ว", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup watch ad button
        watchAdButton.setOnClickListener {
            if (isNetworkAvailable()) {
                if (rewardedAd != null) {
                    showRewardedAd()
                } else {
                    Toast.makeText(this, "โฆษณายังไม่พร้อม กรุณารอสักครู่", Toast.LENGTH_SHORT).show()
                    loadRewardedAd() // Try to load again if not ready
                }
            } else {
                Toast.makeText(this, "กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
            }
        }

        // Load user's check-in data
        if (isNetworkAvailable()) {
            setupCheckInListener()
            setupClaimedRewardsListener()
            loadUserData()
        } else {
            Toast.makeText(this, "กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
        }

        // Show the wheel of fortune card
        wheelOfFortuneCard.visibility = View.VISIBLE

        // Load the first rewarded ad when the activity starts
        loadRewardedAd()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setupCheckInListener() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkInListener?.remove()

            checkInListener = firestore.collection("checkins")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Error in check-in listener: ${e.message}")
                        if (e.message?.contains("FAILED_PRECONDITION") == true) {
                            // แสดงลิงก์สำหรับสร้าง index
                            val indexUrl = "https://console.firebase.google.com/v1/r/project/sethpos/firestore/indexes?create_composite=Ckhwcm9qZWN0cy9zZXRocG9zL2RhdGFiYXNlcy8oZGVmYXVsdCkvY29sbGVjdGlvbkdyb3Vwcy9jaGVja2lucy9pbmRleGVzL18QARoKCgZ1c2VySWQQARoNCgl0aW1lc3RhbXAQAhoMCghfX25hbWVfXxAC"
                            Toast.makeText(this, "กรุณาสร้าง index ที่จำเป็น: $indexUrl", Toast.LENGTH_LONG).show()
                            
                            // ลองใช้วิธี query แบบไม่มี orderBy
                            firestore.collection("checkins")
                                .whereEqualTo("userId", currentUser.uid)
                                .get()
                                .addOnSuccessListener { documents ->
                                    processCheckInDocuments(documents.documents)
                                }
                                .addOnFailureListener { error ->
                                    Log.e(TAG, "Error fetching check-ins: ${error.message}")
                                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูล: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูล: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        return@addSnapshotListener
                    }

                    snapshot?.let { documents ->
                        processCheckInDocuments(documents.documents)
                    }
                }
        }
    }

    private fun processCheckInDocuments(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            totalCheckIns = documents.size
            
            if (documents.isNotEmpty()) {
                val lastCheckIn = documents[0].getTimestamp("timestamp")?.toDate()
                lastCheckInDate = lastCheckIn
                
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                
                if (lastCheckIn?.after(today) == true) {
                    textCheckInStatus.setText(R.string.checked_in_today)
                    textCheckInTime.text = getString(R.string.check_in_time, formatTime(lastCheckIn))
                    checkInButton.isEnabled = false
                    animateCheckInStatus(true)
                } else {
                    textCheckInStatus.setText(R.string.not_checked_in_today)
                    textCheckInTime.text = getString(R.string.last_check_in_time, formatTime(lastCheckIn))
                    checkInButton.isEnabled = true
                    animateCheckInStatus(false)
                }

                calculateStreak(documents)
            }

            animateStreakCount()
            textTotalCheckIns.text = getString(R.string.total_check_ins, totalCheckIns)

            loadRewards()
            loadUserData()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing check-in data: ${e.message}")
            Toast.makeText(this, "เกิดข้อผิดพลาดในการประมวลผลข้อมูล", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClaimedRewardsListener() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            claimedRewardsListener?.remove()

            claimedRewardsListener = firestore.collection("claimed_rewards")
                .whereEqualTo("userId", currentUser.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    snapshot?.let { documents ->
                        // Update rewards based on claimed status
                        loadRewards()
                        loadUserData()
                    }
                }
        }
    }

    private fun animateCheckInButton() {
        val scaleX = ObjectAnimator.ofFloat(checkInButton, "scaleX", 1f, 0.9f, 1f)
        val scaleY = ObjectAnimator.ofFloat(checkInButton, "scaleY", 1f, 0.9f, 1f)
        val alpha = ObjectAnimator.ofFloat(checkInButton, "alpha", 1f, 0.5f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateCheckInStatus(isCheckedIn: Boolean) {
        val targetColor = if (isCheckedIn) {
            getColor(R.color.success)
        } else {
            getColor(R.color.text_primary_dark)
        }

        ValueAnimator.ofArgb(
            getColor(R.color.text_primary_dark),
            targetColor
        ).apply {
            duration = 500
            addUpdateListener { animator ->
                textCheckInStatus.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun animateStreakCount() {
        val oldStreak = textStreakCount.text.toString().filter { it.isDigit() }.toIntOrNull() ?: 0
        val animator = ValueAnimator.ofInt(oldStreak, currentStreak)
        
        animator.apply {
            duration = 1000
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textStreakCount.text = getString(R.string.streak_days, value)
            }
            start()
        }
    }

    private fun calculateStreak(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        var streak = 0
        val calendar = Calendar.getInstance()
        calendar.time = Date()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val today = calendar.time
        
        for (i in documents.indices) {
            val checkInDate = documents[i].getTimestamp("timestamp")?.toDate()
            if (checkInDate != null) {
                calendar.time = checkInDate
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                if (i == 0) {
                    // First check-in (most recent)
                    if (calendar.time == today) {
                        streak = 1
                    } else {
                        break
                    }
                } else {
                    // Check if consecutive days
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val previousCheckIn = documents[i - 1].getTimestamp("timestamp")?.toDate()
                    if (previousCheckIn != null) {
                        val prevCalendar = Calendar.getInstance()
                        prevCalendar.time = previousCheckIn
                        prevCalendar.set(Calendar.HOUR_OF_DAY, 0)
                        prevCalendar.set(Calendar.MINUTE, 0)
                        prevCalendar.set(Calendar.SECOND, 0)
                        prevCalendar.set(Calendar.MILLISECOND, 0)
                        
                        if (calendar.time == prevCalendar.time) {
                            streak++
                        } else {
                            break
                        }
                    }
                }
            }
        }
        
        currentStreak = streak
    }

    private fun loadRewards() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Get claimed rewards
            firestore.collection("claimed_rewards")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .addOnSuccessListener { claimedDocuments ->
                    val claimedRewardIds = claimedDocuments.documents.mapNotNull { it.getString("rewardId") }.toSet()

                    // Create base rewards
                    val rewards = listOf(
                        Reward(
                            id = "1",
                            title = "หมุนวงล้อ 2 ครั้ง",
                            description = "เช็คอิน 3 วันติดต่อกัน",
                            requiredStreak = 3,
                            spins = 2
                        ),
                        Reward(
                            id = "2",
                            title = "หมุนวงล้อ 3 ครั้ง",
                            description = "เช็คอิน 7 วันติดต่อกัน",
                            requiredStreak = 7,
                            spins = 3
                        ),
                        Reward(
                            id = "3",
                            title = "หมุนวงล้อ 5 ครั้ง",
                            description = "เช็คอิน 15 วันติดต่อกัน",
                            requiredStreak = 15,
                            spins = 5
                        )
                    )

                    // Update rewards based on current streak and claimed status
                    val updatedRewards = rewards.map { reward ->
                        reward.copy(
                            isAvailable = currentStreak >= reward.requiredStreak,
                            isClaimed = claimedRewardIds.contains(reward.id)
                        )
                    }

                    rewardAdapter.submitList(updatedRewards)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading rewards: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดรางวัล", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showRewardDetails(reward: Reward) {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val claimedDate = reward.claimedAt?.let { dateFormat.format(it) } ?: "-"
            
            val message = """
                🎁 ${reward.title}
                🎯 จำนวนครั้งที่หมุน: ${reward.spins} ครั้ง
                📅 รับเมื่อ: $claimedDate
            """.trimIndent()

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing reward details: ${e.message}")
            Toast.makeText(this, "ไม่สามารถแสดงรายละเอียดรางวัลได้", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performCheckIn() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val now = Date()
            val checkInData = hashMapOf(
                "userId" to currentUser.uid,
                "timestamp" to now,
                "location" to "ร้านค้า"
            )

            firestore.collection("checkins")
                .add(checkInData)
                .addOnSuccessListener {
                    Toast.makeText(this, "เช็คอินสำเร็จ", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "เช็คอินล้มเหลว: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "กรุณาเข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun claimReward(reward: Reward) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val rewardData = hashMapOf(
                "userId" to currentUser.uid,
                "rewardId" to reward.id,
                "claimedAt" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("claimed_rewards")
                .add(rewardData)
                .addOnSuccessListener {
                    Toast.makeText(this, "รับรางวัลสำเร็จ!", Toast.LENGTH_SHORT).show()
                    // Update remaining spins and show wheel
                    remainingSpins += reward.spins
                    updateRemainingSpinsText()
                    wheelOfFortuneCard.visibility = View.VISIBLE
                    Toast.makeText(this, "คุณได้รับสิทธิ์หมุนวงล้อ ${reward.spins} ครั้ง!", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการรับรางวัล", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun spinWheel() {
        val degrees = Random.nextFloat() * 360f // Random angle from 0 to 359.99
        val rotate = ObjectAnimator.ofFloat(wheelImageView, "rotation", 0f, 3600f + degrees)
        rotate.duration = 5000 // Spin duration
        rotate.interpolator = AccelerateDecelerateInterpolator()

        rotate.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)

                remainingSpins--
                updateRemainingSpinsInFirestore()

                // Determine the landed color based on the final angle
                val landedColor = getLandedColor(degrees)
                Log.d(TAG, "spinWheel: Landed color: $landedColor (Degrees: $degrees)")
                val rewardsForColor = rewardMapping[landedColor]

                if (rewardsForColor != null && rewardsForColor.isNotEmpty()) {
                    if (rewardsForColor.contains("NO_REWARD")) {
                        Toast.makeText(this@CheckInActivity, getString(R.string.no_reward_this_time), Toast.LENGTH_LONG).show()
                        Log.d(TAG, "spinWheel: Landed on $landedColor, which has NO_REWARD.")
                    } else {
                        val chosenReward = rewardsForColor.random() // Randomly pick one reward if multiple options
                        Log.d(TAG, "spinWheel: Landed on $landedColor, chosen reward: $chosenReward")
                        grantSpecificReward(chosenReward)
                        Toast.makeText(this@CheckInActivity, "คุณได้รับ: ${getDisplayRewardName(chosenReward)}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@CheckInActivity, getString(R.string.no_reward_this_time), Toast.LENGTH_LONG).show()
                    Log.d(TAG, "spinWheel: Landed on $landedColor, no rewards configured for this color.")
                }
            }
        })
        rotate.start()
    }

    private fun getLandedColor(degrees: Float): String {
        val normalizedDegrees = (degrees + 360) % 360 // Ensure degrees are positive and within 0-359

        return when (normalizedDegrees) {
            in 355.0..360.0, in 0.0..5.0 -> "Red" // 10 degrees (5 + 5)
            in 5.0..90.0 -> "Orange" // 85 degrees
            in 90.0..175.0 -> "Yellow" // 85 degrees
            in 175.0..185.0 -> "Green" // 10 degrees
            in 185.0..270.0 -> "Blue" // 85 degrees
            in 270.0..355.0 -> "Purple" // 85 degrees
            else -> "Unknown"
        }
    }

    private fun getDisplayRewardName(rewardType: String): String {
        return when (rewardType) {
            "POST_PERMISSION_1" -> getString(R.string.reward_post_1_time)
            "POST_PERMISSION_2" -> getString(R.string.reward_post_2_times)
            "UNLIMITED_POSTS_1_DAY" -> getString(R.string.reward_unlimited_posts_1_day)
            "UNLIMITED_NOTIFICATIONS_1_DAY" -> getString(R.string.reward_unlimited_notifications_1_day)
            "NO_REWARD" -> getString(R.string.no_reward_this_time)
            else -> "รางวัลที่ไม่รู้จัก"
        }
    }

    private fun grantSpecificReward(rewardType: String) {
        Log.d(TAG, "grantSpecificReward called for rewardType: $rewardType")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = firestore.collection("users").document(currentUser.uid)
            val updates = hashMapOf<String, Any>()

            when (rewardType) {
                "POST_PERMISSION_1" -> {
                    postPermissions += 1
                    updates["postPermissions"] = FieldValue.increment(1)
                    userDocRef.update(updates)
                        .addOnSuccessListener { Log.d(TAG, "Granted 1 post permission. Total: $postPermissions. Firestore update successful.") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error granting post permission: ${e.message}") }
                }
                "POST_PERMISSION_2" -> {
                    postPermissions += 2
                    updates["postPermissions"] = FieldValue.increment(2)
                    userDocRef.update(updates)
                        .addOnSuccessListener { Log.d(TAG, "Granted 2 post permissions. Total: $postPermissions. Firestore update successful.") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error granting post permissions: ${e.message}") }
                }
                "UNLIMITED_POSTS_1_DAY" -> {
                    val oneDayFromNow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    unlimitedPostsUntil = oneDayFromNow
                    updates["unlimitedPostsUntil"] = oneDayFromNow
                    userDocRef.update(updates)
                        .addOnSuccessListener { Log.d(TAG, "Granted unlimited posts for 1 day. Until: $unlimitedPostsUntil. Firestore update successful.") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error granting unlimited posts: ${e.message}") }
                }
                "UNLIMITED_NOTIFICATIONS_1_DAY" -> {
                    val oneDayFromNow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    unlimitedNotificationsUntil = oneDayFromNow
                    updates["unlimitedNotificationsUntil"] = oneDayFromNow
                    userDocRef.update(updates)
                        .addOnSuccessListener { Log.d(TAG, "Granted unlimited notifications for 1 day. Until: $unlimitedNotificationsUntil. Firestore update successful.") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error granting unlimited notifications: ${e.message}") }
                }
                "NO_REWARD" -> {
                    Log.d(TAG, "No reward for this spin.")
                }
            }
        } else {
            Log.w(TAG, "grantSpecificReward: currentUser is null. Cannot grant reward.")
        }
    }

    private fun updateRemainingSpinsInFirestore() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users")
                .document(currentUser.uid)
                .update("remainingSpins", remainingSpins)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving remaining spins: ${e.message}")
                }
        }
    }

    private fun updateRemainingSpinsText() {
        remainingSpinsText.text = getString(R.string.remaining_spins, remainingSpins)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userDocRef = firestore.collection("users").document(currentUser.uid)

            userDocRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        remainingSpins = document.getLong("remainingSpins")?.toInt() ?: 0
                        dailyAdWatches = document.getLong("dailyAdWatches")?.toInt() ?: 0
                        val lastAdWatchDateMillis = document.getLong("lastAdWatchDate") ?: 0L
                        val lastAdWatchDate = Date(lastAdWatchDateMillis)

                        postPermissions = document.getLong("postPermissions")?.toInt() ?: 0
                        unlimitedPostsUntil = document.getLong("unlimitedPostsUntil") ?: 0L
                        unlimitedNotificationsUntil = document.getLong("unlimitedNotificationsUntil") ?: 0L

                        val today = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time

                        if (lastAdWatchDate.before(today)) {
                            dailyAdWatches = 0
                            updateDailyAdWatchesInFirestore(dailyAdWatches, true)
                        }
                    } else {
                        // Initialize new user with default values
                        remainingSpins = 3 // Initial spins for new users
                        dailyAdWatches = 0
                        postPermissions = 0
                        unlimitedPostsUntil = 0L
                        unlimitedNotificationsUntil = 0L

                        val initialData = hashMapOf<String, Any>(
                            "remainingSpins" to remainingSpins,
                            "dailyAdWatches" to dailyAdWatches,
                            "lastAdWatchDate" to System.currentTimeMillis(),
                            "postPermissions" to postPermissions,
                            "unlimitedPostsUntil" to unlimitedPostsUntil,
                            "unlimitedNotificationsUntil" to unlimitedNotificationsUntil,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                        userDocRef.set(initialData)
                            .addOnSuccessListener { Log.d(TAG, "New user data initialized.") }
                            .addOnFailureListener { e -> Log.e(TAG, "Error initializing new user data: ${e.message}") }
                    }
                    remainingSpinsText.text = getString(R.string.remaining_spins, remainingSpins)
                    updateWatchAdButtonState()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading user data: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateWatchAdButtonState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            watchAdButton.isEnabled = false
            watchAdButton.text = "เข้าสู่ระบบเพื่อดูโฆษณา"
            Log.d(TAG, "updateWatchAdButtonState: User not logged in, button disabled.")
            return
        }

        if (dailyAdWatches >= MAX_DAILY_AD_WATCHES) {
            watchAdButton.isEnabled = false
            watchAdButton.text = "ดูโฆษณา (จำกัดต่อวัน)"
            Log.d(TAG, "updateWatchAdButtonState: Daily ad watch limit reached, button disabled.")
        } else if (rewardedAd == null) {
            watchAdButton.isEnabled = false
            watchAdButton.text = "กำลังโหลดโฆษณา..."
            Log.d(TAG, "updateWatchAdButtonState: Rewarded ad is null, button disabled.")
        } else {
            watchAdButton.isEnabled = true
            watchAdButton.text = "ดูโฆษณาเพื่อได้รับสิทธิ์หมุนวงล้อ ($dailyAdWatches/$MAX_DAILY_AD_WATCHES)"
            Log.d(TAG, "updateWatchAdButtonState: Rewarded ad is ready, button enabled.")
        }
    }

    private fun loadRewardedAd() {
        Log.d(TAG, "loadRewardedAd called.")
        if (dailyAdWatches >= MAX_DAILY_AD_WATCHES) {
            Log.d(TAG, "Daily ad watch limit reached. Not loading ad.")
            updateWatchAdButtonState()
            return
        }

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Rewarded ad failed to load: ${adError.message}")
                rewardedAd = null
                Toast.makeText(this@CheckInActivity, "ไม่สามารถโหลดโฆษณาได้", Toast.LENGTH_SHORT).show()
                updateWatchAdButtonState()
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Rewarded ad was loaded. (from callback)")
                rewardedAd = ad
                updateWatchAdButtonState()
            }
        })
    }

    private fun showRewardedAd() {
        Log.d(TAG, "showRewardedAd called. rewardedAd is null: ${rewardedAd == null}")
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad was shown.")
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    Toast.makeText(this@CheckInActivity, "ไม่สามารถแสดงโฆษณาได้", Toast.LENGTH_SHORT).show()
                    rewardedAd = null
                    loadRewardedAd() // Try to load a new ad
                    updateWatchAdButtonState()
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad was dismissed.")
                    rewardedAd = null
                    loadRewardedAd() // Load a new ad for the next watch
                    updateWatchAdButtonState()
                }
            }

            rewardedAd?.show(this) { rewardItem ->
                // Grant reward to user
                Log.d(TAG, "User earned the reward. Reward amount: ${rewardItem.amount}, type: ${rewardItem.type}")
                grantSpinReward()
                incrementDailyAdWatches()
            }
        } else {
            Log.d(TAG, "The rewarded ad wasn't ready yet. (from showRewardedAd)")
            Toast.makeText(this, "โฆษณายังไม่พร้อม กรุณารอสักครู่", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    private fun grantSpinReward() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .update("remainingSpins", FieldValue.increment(1))
                .addOnSuccessListener {
                    Log.d(TAG, "Spin reward granted.")
                    remainingSpins++
                    remainingSpinsText.text = getString(R.string.remaining_spins, remainingSpins)
                    Toast.makeText(this, "ได้รับสิทธิ์หมุนวงล้อแล้ว!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error granting spin reward: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการมอบรางวัลสิทธิ์หมุนวงล้อ", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun incrementDailyAdWatches() {
        dailyAdWatches++
        updateDailyAdWatchesInFirestore(dailyAdWatches, false)
    }

    private fun updateDailyAdWatchesInFirestore(count: Int, resetDate: Boolean) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val updates = hashMapOf<String, Any>(
                "dailyAdWatches" to count
            )
            if (resetDate) {
                updates["lastAdWatchDate"] = System.currentTimeMillis()
            }

            firestore.collection("users").document(currentUser.uid)
                .update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Daily ad watches updated to $count.")
                    updateWatchAdButtonState()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating daily ad watches: ${e.message}")
                }
        }
    }

    private fun formatTime(date: Date?): String {
        return if (date != null) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            "-"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        checkInListener?.remove()
        claimedRewardsListener?.remove()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "CheckInActivity"
        
        fun newIntent(context: Context): Intent {
            return Intent(context, CheckInActivity::class.java)
        }
    }
} 
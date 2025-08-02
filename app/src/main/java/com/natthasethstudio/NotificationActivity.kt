package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.natthasethstudio.sethpos.adapter.NotificationAdapter
import com.natthasethstudio.sethpos.model.Notification
import com.natthasethstudio.sethpos.SethPOSApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class NotificationActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerViewNotifications: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textViewNoNotifications: LinearLayout
    private lateinit var notificationAdapter: NotificationAdapter
    private var isPremiumUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // Check Google Play Services
        if (!isGooglePlayServicesAvailable()) {
            Toast.makeText(this, "Google Play Services ไม่พร้อมใช้งาน", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        recyclerViewNotifications = findViewById(R.id.recyclerViewNotifications)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        textViewNoNotifications = findViewById(R.id.textViewNoNotifications)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbarNotifications))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Setup RecyclerView
        recyclerViewNotifications.layoutManager = LinearLayoutManager(this)
        notificationAdapter = NotificationAdapter()
        recyclerViewNotifications.adapter = notificationAdapter

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeResources(
            R.color.colorAccent,
            R.color.colorPrimary,
            R.color.like_red
        )
        swipeRefreshLayout.setOnRefreshListener {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                fetchNotifications(currentUserId)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // Set up notification click listener
        notificationAdapter.onNotificationClickListener = { notification ->
            try {
                // Handle notification click based on type
                when (notification.type) {
                    "like", "comment", "boost" -> {
                        // Navigate to the post
                        notification.postId?.let { postId ->
                            val intent = Intent(this, PostDetailActivity::class.java)
                            intent.putExtra("postId", postId)
                            startActivity(intent)
                        }
                    }
                    "follow" -> {
                        // Navigate to user profile
                        val senderId = notification.senderId
                        if (senderId.isNotEmpty()) {
                            val intent = Intent(this, ProfileActivity::class.java)
                            intent.putExtra("userId", senderId)
                            startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error handling notification click: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาดในการเปิดการแจ้งเตือน", Toast.LENGTH_SHORT).show()
            }
        }

        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            try {
                checkPremiumStatus()
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error in onCreate: ${e.message}")
                showEmptyState()
            }
        } else {
            showEmptyState()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.notification_menu, menu)

        // Add test notification menu item for debugging
        menu.add(Menu.NONE, 999, Menu.NONE, "ทดสอบแจ้งเตือน")

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_mark_all_read -> {
                markAllNotificationsAsRead()
                true
            }
            R.id.action_clear_all -> {
                showClearAllConfirmationDialog()
                true
            }
            999 -> {
                createTestNotification()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, 2404)?.show()
            }
            return false
        }
        return true
    }

    private fun checkPremiumStatus() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            try {
                firestore.collection("premium_users").document(currentUserId)
                    .get()
                    .addOnSuccessListener { document ->
                        isPremiumUser = document.exists() && (document.getBoolean("isPremium") ?: false)
                        Log.d("NotificationActivity", "Premium status checked: $isPremiumUser")
                        // Fetch notifications after premium status is determined
                        fetchNotifications(currentUserId)
                    }
                    .addOnFailureListener { error ->
                        Log.e("NotificationActivity", "Error checking premium status: ${error.message}")
                        isPremiumUser = false
                        // Fetch notifications even if premium check fails
                        fetchNotifications(currentUserId)
                    }
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error in checkPremiumStatus: ${e.message}")
                isPremiumUser = false
                // Fetch notifications even if premium check fails
                fetchNotifications(currentUserId)
            }
        }
    }

    private fun fetchNotifications(userId: String) {
        Log.d("NotificationActivity", "Fetching notifications for user: $userId, isPremium: $isPremiumUser")

        // Clear sender cache to ensure fresh data
        notificationAdapter.clearSenderCache()

        var query = firestore.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // ถ้าไม่ใช่สมาชิกพรีเมียม จำกัดจำนวนการแจ้งเตือนที่แสดง
        if (!isPremiumUser) {
            query = query.limit(10)
            Log.d("NotificationActivity", "Limited to 10 notifications for non-premium user")
        } else {
            Log.d("NotificationActivity", "No limit for premium user")
        }

        query.addSnapshotListener { snapshot, e ->
            swipeRefreshLayout.isRefreshing = false

            if (e != null) {
                Log.e("NotificationActivity", "Error fetching notifications: ${e.message}")
                showEmptyState()
                return@addSnapshotListener
            }

            try {
                if (snapshot != null) {
                    val notifications = snapshot.toObjects(Notification::class.java)
                    Log.d("NotificationActivity", "Found ${notifications.size} notifications")

                    if (notifications.isNotEmpty()) {
                        notificationAdapter.submitList(notifications)
                        showNotificationList()

                        // แสดงข้อความสำหรับผู้ใช้ทั่วไปที่ดูการแจ้งเตือนเกิน 10 รายการ
                        if (!isPremiumUser && notifications.size >= 10) {
                            Toast.makeText(this, "คุณสามารถดูการแจ้งเตือนได้เพียง 10 รายการ สมัครสมาชิกพรีเมียมเพื่อดูการแจ้งเตือนได้ไม่จำกัด", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.d("NotificationActivity", "No notifications found, showing empty state")
                        notificationAdapter.submitList(emptyList())
                        showEmptyState()
                    }
                } else {
                    Log.d("NotificationActivity", "Snapshot is null, showing empty state")
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error processing notifications: ${e.message}")
                showEmptyState()
            }
        }
    }

    private fun showNotificationList() {
        recyclerViewNotifications.visibility = View.VISIBLE
        textViewNoNotifications.visibility = View.GONE
    }

    private fun showEmptyState() {
        recyclerViewNotifications.visibility = View.GONE
        textViewNoNotifications.visibility = View.VISIBLE
    }

    private fun markAllNotificationsAsRead() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            try {
                firestore.collection("notifications")
                    .whereEqualTo("recipientId", currentUserId)
                    .whereEqualTo("read", false)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            Toast.makeText(this, "ไม่มีการแจ้งเตือนที่ยังไม่ได้อ่าน", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val batch = firestore.batch()
                        documents.forEach { doc ->
                            batch.update(doc.reference, "read", true)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                Toast.makeText(this, "ทำเครื่องหมายว่าอ่านแล้วทั้งหมด", Toast.LENGTH_SHORT).show()
                                // Update main activity header to hide badge
                                try {
                                    val mainActivity = (application as? SethPOSApplication)?.feedAdapter
                                    mainActivity?.updateHeaderProfile()
                                } catch (e: Exception) {
                                    Log.e("NotificationActivity", "Error updating header: ${e.message}")
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e("NotificationActivity", "Error marking notifications as read: ${error.message}")
                                Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { error ->
                        Log.e("NotificationActivity", "Error fetching notifications to mark as read: ${error.message}")
                        Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error in markAllNotificationsAsRead: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("ลบการแจ้งเตือนทั้งหมด")
            .setMessage("คุณต้องการลบการแจ้งเตือนทั้งหมดใช่หรือไม่? การดำเนินการนี้ไม่สามารถยกเลิกได้")
            .setPositiveButton("ลบ") { _, _ ->
                clearAllNotifications()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun clearAllNotifications() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            try {
                firestore.collection("notifications")
                    .whereEqualTo("recipientId", currentUserId)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            Toast.makeText(this, "ไม่มีการแจ้งเตือนให้ลบ", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val batch = firestore.batch()
                        documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                Toast.makeText(this, "ลบการแจ้งเตือนทั้งหมดแล้ว", Toast.LENGTH_SHORT).show()
                                showEmptyState()
                                // Update main activity header to hide badge
                                try {
                                    val mainActivity = (application as? SethPOSApplication)?.feedAdapter
                                    mainActivity?.updateHeaderProfile()
                                } catch (e: Exception) {
                                    Log.e("NotificationActivity", "Error updating header: ${e.message}")
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e("NotificationActivity", "Error clearing notifications: ${error.message}")
                                Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { error ->
                        Log.e("NotificationActivity", "Error fetching notifications to clear: ${error.message}")
                        Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Error in clearAllNotifications: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createTestNotification() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            val testNotification = Notification(
                recipientId = currentUserId,
                senderId = currentUserId,
                senderName = "ผู้ใช้ทดสอบ",
                senderAvatarId = 0,
                type = "like",
                message = "ได้กดถูกใจโพสต์ของคุณ (ทดสอบ)",
                postId = "test_post_id",
                timestamp = com.google.firebase.Timestamp.now(),
                read = false
            )

            firestore.collection("notifications")
                .add(testNotification)
                .addOnSuccessListener { documentReference ->
                    Log.d("NotificationActivity", "Test notification created: ${documentReference.id}")
                    Toast.makeText(this, "สร้างแจ้งเตือนทดสอบสำเร็จ", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("NotificationActivity", "Error creating test notification: $e")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการสร้างแจ้งเตือนทดสอบ", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Hide the notification badge when entering the notifications screen
            val notificationCountView = findViewById<TextView>(R.id.textNotificationCount)
            notificationCountView?.visibility = View.GONE

            // Update main activity header to hide badge
            val mainActivity = (application as? SethPOSApplication)?.feedAdapter
            mainActivity?.updateHeaderProfile()
        } catch (e: Exception) {
            Log.e("NotificationActivity", "Error in onResume: ${e.message}")
        }
    }
} 
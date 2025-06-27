package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.adapter.NotificationAdapter
import com.natthasethstudio.sethpos.model.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class NotificationActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerViewNotifications: RecyclerView
    private lateinit var textViewNoNotifications: TextView
    private lateinit var btnMarkAllRead: ImageButton
    private lateinit var btnClearAll: ImageButton
    private lateinit var toolbar: Toolbar
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
        textViewNoNotifications = findViewById(R.id.textViewNoNotifications)
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead)
        btnClearAll = findViewById(R.id.btnClearAll)

        // Setup toolbar
        toolbar = findViewById(R.id.toolbarNotifications)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        recyclerViewNotifications.layoutManager = LinearLayoutManager(this)
        notificationAdapter = NotificationAdapter()
        recyclerViewNotifications.adapter = notificationAdapter

        // Set up notification click listener
        notificationAdapter.onNotificationClickListener = { notification ->
            // Handle notification click based on type
            when (notification.type) {
                "like", "comment" -> {
                    // Navigate to the post
                    notification.postId?.let { postId ->
                        val intent = Intent(this, PostDetailActivity::class.java)
                        intent.putExtra("postId", postId)
                        startActivity(intent)
                    }
                }
                "follow" -> {
                    // Navigate to user profile
                    notification.senderId.let { senderId ->
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.putExtra("userId", senderId)
                        startActivity(intent)
                    }
                }
            }
        }

        // Set up mark all as read button
        btnMarkAllRead.setOnClickListener {
            markAllNotificationsAsRead()
        }

        // Set up clear all button
        btnClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }

        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            checkPremiumStatus()
            fetchNotifications(currentUserId)
        } else {
            textViewNoNotifications.visibility = View.VISIBLE
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
        currentUserId?.let { userId ->
            firestore.collection("premium_users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    isPremiumUser = document.exists() && (document.getBoolean("isPremium") ?: false)
                }
        }
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

    private fun fetchNotifications(userId: String) {
        val query = firestore.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // ถ้าไม่ใช่สมาชิกพรีเมียม จำกัดจำนวนการแจ้งเตือนที่แสดง
        if (!isPremiumUser) {
            query.limit(10)
        }

        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val notifications = snapshot.toObjects(Notification::class.java)
                if (notifications.isNotEmpty()) {
                    // Load sender details for each notification
                    notifications.forEach { notification ->
                        firestore.collection("users").document(notification.senderId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val senderName = document.getString("nickname") ?: document.getString("name") ?: "ไม่พบชื่อ"
                                    val avatarId = document.getLong("avatarId")?.toInt() ?: 0
                                    
                                    // Update notification with sender details
                                    firestore.collection("notifications").document(notification.notificationId)
                                        .update(
                                            mapOf(
                                                "senderName" to senderName,
                                                "senderAvatarId" to avatarId
                                            )
                                        )
                                }
                            }
                    }
                    notificationAdapter.submitList(notifications)
                    recyclerViewNotifications.visibility = View.VISIBLE
                    textViewNoNotifications.visibility = View.GONE

                    // แสดงข้อความสำหรับผู้ใช้ทั่วไปที่ดูการแจ้งเตือนเกิน 10 รายการ
                    if (!isPremiumUser && notifications.size >= 10) {
                        Toast.makeText(this, "คุณสามารถดูการแจ้งเตือนได้เพียง 10 รายการ สมัครสมาชิกพรีเมียมเพื่อดูการแจ้งเตือนได้ไม่จำกัด", Toast.LENGTH_LONG).show()
                    }
                } else {
                    notificationAdapter.submitList(emptyList())
                    recyclerViewNotifications.visibility = View.GONE
                    textViewNoNotifications.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun markAllNotificationsAsRead() {
        val currentUserId = auth.currentUser?.uid
        currentUserId?.let { userId ->
            firestore.collection("notifications")
                .whereEqualTo("recipientId", userId)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener { documents ->
                    val batch = firestore.batch()
                    documents.forEach { doc ->
                        batch.update(doc.reference, "read", true)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "ทำเครื่องหมายว่าอ่านแล้วทั้งหมด", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("ลบการแจ้งเตือนทั้งหมด")
            .setMessage("คุณต้องการลบการแจ้งเตือนทั้งหมดใช่หรือไม่?")
            .setPositiveButton("ลบ") { _, _ ->
                clearAllNotifications()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun clearAllNotifications() {
        val currentUserId = auth.currentUser?.uid
        currentUserId?.let { userId ->
            firestore.collection("notifications")
                .whereEqualTo("recipientId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    val batch = firestore.batch()
                    documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "ลบการแจ้งเตือนทั้งหมดแล้ว", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide the notification badge when entering the notifications screen
        val notificationCountView = findViewById<TextView>(R.id.textNotificationCount)
        notificationCountView?.visibility = View.GONE
    }
} 
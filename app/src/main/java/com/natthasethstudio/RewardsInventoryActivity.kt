package com.natthasethstudio.sethpos

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natthasethstudio.sethpos.adapter.RewardAdapter
import com.natthasethstudio.sethpos.databinding.ActivityRewardsInventoryBinding
import com.natthasethstudio.sethpos.model.Reward
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class RewardsInventoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerViewRewards: RecyclerView
    private lateinit var rewardAdapter: RewardAdapter
    private lateinit var textEmptyState: TextView
    private lateinit var binding: ActivityRewardsInventoryBinding

    companion object {
        private const val TAG = "RewardsInventoryActivity"
        private const val PLAY_SERVICES_RESOLUTION_REQUEST = 2404

        fun newIntent(context: Context): Intent {
            return Intent(context, RewardsInventoryActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRewardsInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(this, "กรุณาเข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        recyclerViewRewards = binding.recyclerViewRewards
        textEmptyState = binding.textEmptyState

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "คลังรางวัล"
        }

        // Set up RecyclerView
        recyclerViewRewards.layoutManager = LinearLayoutManager(this)
        rewardAdapter = RewardAdapter { reward ->
            showRewardDetails(reward)
        }
        recyclerViewRewards.adapter = rewardAdapter

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Check network connectivity first
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Load data
        loadClaimedRewards()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkGooglePlayServicesAndLoadData() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                loadClaimedRewards()
            }
            else -> {
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    googleApiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)?.show()
                } else {
                    Log.e(TAG, "This device is not supported")
                    Toast.makeText(this, "อุปกรณ์นี้ไม่รองรับ Google Play Services", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PLAY_SERVICES_RESOLUTION_REQUEST) {
            if (resultCode == RESULT_OK) {
                loadClaimedRewards()
            } else {
                Toast.makeText(this, "ไม่สามารถใช้งาน Google Play Services ได้", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadClaimedRewards() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("claimed_rewards")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    val rewards = documents.documents.mapNotNull { doc ->
                        val rewardId = doc.getString("rewardId")
                        val claimedAt = doc.getTimestamp("claimedAt")?.toDate()
                        
                        when (rewardId) {
                            "1" -> Reward(
                                id = "1",
                                title = "หมุนวงล้อ 2 ครั้ง",
                                description = "เช็คอิน 3 วันติดต่อกัน",
                                requiredStreak = 3,
                                spins = 2,
                                isClaimed = true,
                                claimedAt = claimedAt
                            )
                            "2" -> Reward(
                                id = "2",
                                title = "หมุนวงล้อ 3 ครั้ง",
                                description = "เช็คอิน 7 วันติดต่อกัน",
                                requiredStreak = 7,
                                spins = 3,
                                isClaimed = true,
                                claimedAt = claimedAt
                            )
                            "3" -> Reward(
                                id = "3",
                                title = "หมุนวงล้อ 5 ครั้ง",
                                description = "เช็คอิน 15 วันติดต่อกัน",
                                requiredStreak = 15,
                                spins = 5,
                                isClaimed = true,
                                claimedAt = claimedAt
                            )
                            else -> null
                        }
                    }

                    rewardAdapter.submitList(rewards)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading claimed rewards: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดรางวัล", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showRewardDetails(reward: Reward) {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val claimedDate = reward.claimedAt?.let { dateFormat.format(it) } ?: "-"
            val expiresDate = reward.expiresAt?.let { dateFormat.format(it) } ?: "-"
            
            val isExpired = reward.expiresAt?.before(Date()) == true
            val status = if (isExpired) "หมดอายุแล้ว" else "ยังไม่หมดอายุ"
            
            val message = """
                🎁 ${reward.title}
                🎯 จำนวนครั้งที่หมุน: ${reward.spins} ครั้ง
                📅 รับเมื่อ: $claimedDate
                ⏰ หมดอายุ: $expiresDate
                📌 สถานะ: $status
            """.trimIndent()

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing reward details: ${e.message}")
            Toast.makeText(this, "ไม่สามารถแสดงรายละเอียดรางวัลได้", Toast.LENGTH_SHORT).show()
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
} 
package com.natthasethstudio.sethpos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.natthasethstudio.sethpos.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID
import android.util.Log
import androidx.core.content.ContextCompat
import com.natthasethstudio.sethpos.SethPOSApplication
import com.google.firebase.firestore.FieldValue
import com.natthasethstudio.sethpos.model.Notification
import com.google.firebase.Timestamp


class ProfileActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: android.content.Context): Intent {
            return Intent(context, ProfileActivity::class.java)
        }
    }

    private lateinit var binding: ActivityProfileBinding
    private var selectedAvatarId: Int = 0
    private var currentProfileImageUrl: String? = null
    private var targetUserId: String? = null
    private var isFollowing: Boolean = false

    private val avatarResources = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6,
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupToolbar()
        setupViews()
        setupListeners()

        val userId = intent.getStringExtra("userId")
        if (userId != null) {
            targetUserId = userId
            loadUserProfile(userId)
        } else {
            finish()
        }

        setupBackPressCallback()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarProfile)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun setupViews() {
        // Create avatar chips
        avatarResources.forEachIndexed { index, avatarResId ->
            val avatarView = layoutInflater.inflate(R.layout.item_avatar, binding.avatarContainer, false) as FrameLayout
            val avatarImage = avatarView.findViewById<ImageView>(R.id.avatarImage)
            val selectedIndicator = avatarView.findViewById<ImageView>(R.id.selectedIndicator)
            
            avatarImage.setImageResource(avatarResId)
            selectedIndicator.visibility = if (index == selectedAvatarId) View.VISIBLE else View.GONE
            
            avatarView.setOnClickListener {
                selectedAvatarId = index
                binding.currentAvatarImage.setImageResource(avatarResId)
                updateAvatarSelectionUI(index)
            }
            
            binding.avatarContainer.addView(avatarView)
        }
    }

    private fun setupListeners() {
        binding.buttonLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        binding.buttonSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.changeProfileImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.buttonFollowToggle.setOnClickListener {
            toggleFollow()
        }
    }

    private fun setupBackPressCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun loadUserProfile(userId: String) {
        val currentUserId = auth.currentUser?.uid

        if (userId == currentUserId) {
            binding.buttonFollowToggle.visibility = View.GONE
            binding.buttonSaveChanges.visibility = View.VISIBLE
            binding.changeProfileImageButton.visibility = View.VISIBLE
            binding.textInputLayoutPassword.visibility = View.VISIBLE
            binding.textInputLayoutNickname.visibility = View.VISIBLE
            binding.avatarContainer.visibility = View.VISIBLE
        } else {
            binding.buttonSaveChanges.visibility = View.GONE
            binding.changeProfileImageButton.visibility = View.GONE
            binding.textInputLayoutPassword.visibility = View.GONE
            binding.textInputLayoutNickname.visibility = View.GONE
            binding.avatarContainer.visibility = View.GONE
            binding.buttonFollowToggle.visibility = View.VISIBLE
            checkFollowStatus(userId, currentUserId)
        }

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    document.data?.let { data ->
                        binding.apply {
                            textViewProfileName.text = data["name"] as? String ?: "ไม่พบชื่อ"
                            textViewProfileEmail.text = data["email"] as? String ?: "ไม่พบอีเมล"
                            editTextProfilePassword.setText(data["password"] as? String ?: "ไม่พบรหัสผ่าน")
                            editTextProfileNickname.setText(data["nickname"] as? String ?: "")
                            
                            val avatarId = (data["avatarId"] as? Number)?.toInt() ?: 0
                            currentAvatarImage.setImageResource(avatarResources[avatarId])
                            selectedAvatarId = avatarId
                            currentProfileImageUrl = data["profileImageUrl"] as? String

                            updateAvatarSelectionUI(avatarId)
                        }
                    }
                } else {
                    showProfileNotFound()
                }
            }
            .addOnFailureListener { e ->
                showError("เกิดข้อผิดพลาดในการโหลดข้อมูลโปรไฟล์: ${e.message}")
            }
    }

    private fun checkFollowStatus(targetUserId: String, currentUserId: String?) {
        if (currentUserId == null) return

        firestore.collection("users").document(currentUserId)
            .collection("following").document(targetUserId)
            .get()
            .addOnSuccessListener { document ->
                isFollowing = document.exists()
                updateFollowButtonUI()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileActivity", "Error checking follow status: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาดในการตรวจสอบสถานะการติดตาม", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFollowButtonUI() {
        if (isFollowing) {
            binding.buttonFollowToggle.text = "เลิกติดตาม"
            binding.buttonFollowToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
        } else {
            binding.buttonFollowToggle.text = "ติดตาม"
            binding.buttonFollowToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        }
    }

    private fun toggleFollow() {
        val currentUserId = auth.currentUser?.uid ?: return
        val targetId = targetUserId ?: return

        if (isFollowing) {
            firestore.collection("users").document(currentUserId).collection("following").document(targetId).delete()
            firestore.collection("users").document(targetId).update("followers", FieldValue.arrayRemove(currentUserId))
                .addOnSuccessListener {
                    isFollowing = false
                    updateFollowButtonUI()
                    Toast.makeText(this, "เลิกติดตามแล้ว", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileActivity", "Error unfollowing: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการเลิกติดตาม", Toast.LENGTH_SHORT).show()
                }
        } else {
            firestore.collection("users").document(currentUserId).collection("following").document(targetId).set(mapOf("timestamp" to Timestamp.now()))
            firestore.collection("users").document(targetId).update("followers", FieldValue.arrayUnion(currentUserId))
                .addOnSuccessListener {
                    isFollowing = true
                    updateFollowButtonUI()
                    Toast.makeText(this, "ติดตามแล้ว", Toast.LENGTH_SHORT).show()
                    sendNotification(targetId, "follow")
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileActivity", "Error following: ${e.message}")
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการติดตาม", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendNotification(recipientId: String?, type: String) {
        if (recipientId == null || auth.currentUser?.uid == null || auth.currentUser?.uid == recipientId) {
            return
        }

        firestore.collection("users").document(auth.currentUser!!.uid)
            .get()
            .addOnSuccessListener { senderDoc ->
                val senderName = senderDoc.getString("nickname") ?: senderDoc.getString("name") ?: "ไม่พบชื่อ"
                val senderAvatarId = senderDoc.getLong("avatarId")?.toInt() ?: 0

                val notificationMessage = when (type) {
                    "follow" -> "$senderName ได้ติดตามคุณ"
                    else -> "มีการแจ้งเตือนใหม่"
                }

                val notification = Notification(
                    recipientId = recipientId,
                    senderId = auth.currentUser!!.uid,
                    senderName = senderName,
                    senderAvatarId = senderAvatarId,
                    type = type,
                    message = notificationMessage,
                    timestamp = Timestamp.now(),
                    read = false
                )

                firestore.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener { documentReference ->
                        Log.d("ProfileActivity", "Notification sent successfully: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileActivity", "Error sending notification: $e")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileActivity", "Error fetching sender details for notification: $e")
            }
    }

    private fun showProfileNotFound() {
        Toast.makeText(this, "ไม่พบข้อมูลโปรไฟล์สำหรับผู้ใช้นี้", Toast.LENGTH_SHORT).show()
        binding.apply {
            textViewProfileName.text = "ไม่พบชื่อ"
            textViewProfileEmail.text = "ไม่พบอีเมล"
            editTextProfilePassword.setText("ไม่พบรหัสผ่าน")
            editTextProfileNickname.setText("")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.apply {
            textViewProfileName.text = "เกิดข้อผิดพลาด"
            textViewProfileEmail.text = "เกิดข้อผิดพลาด"
            editTextProfilePassword.setText("เกิดข้อผิดพลาด")
            editTextProfileNickname.setText("เกิดข้อผิดพลาด")
        }
    }

    private fun updateAvatarSelectionUI(selectedId: Int) {
        for (i in 0 until binding.avatarContainer.childCount) {
            val child = binding.avatarContainer.getChildAt(i) as FrameLayout
            val selectedIndicator = child.findViewById<ImageView>(R.id.selectedIndicator)
            selectedIndicator.visibility = if (i == selectedId) View.VISIBLE else View.GONE
        }
    }

    private fun saveChanges() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "คุณยังไม่ได้เข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            return
        }

        val newNickname = binding.editTextProfileNickname.text?.toString()?.trim() ?: ""
        if (newNickname.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกชื่อเล่น", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "nickname" to newNickname,
            "avatarId" to selectedAvatarId
        )

        firestore.collection("users").document(currentUser.uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "บันทึกการเปลี่ยนแปลงสำเร็จ", Toast.LENGTH_SHORT).show()
                binding.textViewProfileName.text = newNickname
                updateFeedAdapter()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "บันทึกการเปลี่ยนแปลงล้มเหลว: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFeedAdapter() {
        (application as? SethPOSApplication)?.feedAdapter?.let { adapter ->
            adapter.clearUserCache()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = result.data?.data
            selectedImageUri?.let {
                uploadImageToFirebase(it)
            }
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_images/${currentUser.uid}/${UUID.randomUUID()}")
        
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    currentProfileImageUrl = downloadUri.toString()
                    firestore.collection("users").document(currentUser.uid)
                        .update(
                            mapOf(
                                "profileImageUrl" to currentProfileImageUrl,
                                "avatarId" to null
                            )
                        )
                        .addOnSuccessListener {
                            Glide.with(this).load(currentProfileImageUrl).into(binding.currentAvatarImage)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "เกิดข้อผิดพลาดในการอัปเดตรูปโปรไฟล์: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "เกิดข้อผิดพลาดในการอัปโหลดรูปภาพ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

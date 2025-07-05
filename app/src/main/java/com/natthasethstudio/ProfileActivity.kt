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
import com.natthasethstudio.sethpos.util.ProfileBackgroundManager


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
    private lateinit var backgroundManager: ProfileBackgroundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        backgroundManager = ProfileBackgroundManager(this)

        setupViews()
        setupListeners()

        // ซ่อน ScrollView และแสดง ProgressBar ตอนเริ่มโหลด
        binding.profileProgressBar.visibility = View.VISIBLE
        binding.root.findViewById<android.widget.ScrollView>(R.id.scrollViewProfile)?.visibility = View.INVISIBLE

        val userId = intent.getStringExtra("userId")
        if (userId != null) {
            targetUserId = userId
            loadUserProfile(userId)
        } else {
            finish()
        }

        setupBackPressCallback()
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

        binding.changeBackgroundButton.setOnClickListener {
            changeBackground()
        }
        
        // Setup animated animals switch
        binding.switchAnimatedAnimals.setOnCheckedChangeListener { _, isChecked ->
            com.natthasethstudio.sethpos.util.SettingsManager.setAnimatedAnimalsEnabled(this, isChecked)
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
            binding.buttonSaveChanges.visibility = View.VISIBLE
            binding.changeProfileImageButton.visibility = View.VISIBLE
            binding.changeBackgroundButton.visibility = View.VISIBLE
            binding.textInputLayoutPassword.visibility = View.VISIBLE
            binding.textInputLayoutNickname.visibility = View.VISIBLE
            binding.avatarContainer.visibility = View.VISIBLE
        } else {
            binding.buttonSaveChanges.visibility = View.GONE
            binding.changeProfileImageButton.visibility = View.GONE
            binding.changeBackgroundButton.visibility = View.GONE
            binding.textInputLayoutPassword.visibility = View.GONE
            binding.textInputLayoutNickname.visibility = View.GONE
            binding.avatarContainer.visibility = View.GONE
        }

        // โหลดพื้นหลังสำหรับผู้ใช้
        loadBackgroundForUser(userId)

        // แสดง ProgressBar และซ่อน ScrollView
        binding.profileProgressBar.visibility = View.VISIBLE
        binding.root.findViewById<android.widget.ScrollView>(R.id.scrollViewProfile)?.visibility = View.INVISIBLE

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    document.data?.let { data ->
                        binding.apply {
                            val name = data["name"] as? String
                            val nickname = data["nickname"] as? String
                            val email = data["email"] as? String
                            textViewProfileName.text =
                                when {
                                    !name.isNullOrBlank() -> name
                                    !nickname.isNullOrBlank() -> nickname
                                    !email.isNullOrBlank() -> email
                                    else -> "ผู้ใช้ใหม่"
                                }
                            textViewProfileEmail.text = data["email"] as? String ?: "ไม่พบอีเมล"
                            editTextProfilePassword.setText(data["password"] as? String ?: "ไม่พบรหัสผ่าน")
                            editTextProfileNickname.setText(data["nickname"] as? String ?: "")
                            
                            // ตรวจสอบว่าเป็น Google user หรือไม่
                            val isGoogleUser = data["isGoogleUser"] as? Boolean ?: false
                            val profileImageUrl = data["profileImageUrl"] as? String
                            
                            // ตรวจสอบ Google user จากหลายวิธี (รองรับข้อมูลเก่า)
                            val isGoogleUserFromUrl = !profileImageUrl.isNullOrEmpty() && 
                                                     profileImageUrl.contains("googleusercontent.com")
                            val isGoogleUserFromProvider = auth.currentUser?.providerData?.any { 
                                it.providerId == "google.com" 
                            } ?: false
                            
                            val isActuallyGoogleUser = isGoogleUser || isGoogleUserFromUrl || isGoogleUserFromProvider
                            
                            if (isActuallyGoogleUser && !profileImageUrl.isNullOrEmpty()) {
                                // ใช้รูป Gmail
                                Glide.with(this@ProfileActivity)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .circleCrop()
                                    .into(currentAvatarImage)
                                
                                // ซ่อน avatar selection สำหรับ Google user
                                avatarContainer.visibility = View.GONE
                            } else {
                                // ใช้ avatar ปกติ
                                val avatarId = (data["avatarId"] as? Number)?.toInt() ?: 0
                                currentAvatarImage.setImageResource(avatarResources[avatarId])
                                selectedAvatarId = avatarId
                                updateAvatarSelectionUI(avatarId)
                            }
                            
                            currentProfileImageUrl = profileImageUrl
                            
                            // Load settings
                            if (userId == currentUserId) {
                                binding.switchAnimatedAnimals.isChecked = 
                                    com.natthasethstudio.sethpos.util.SettingsManager.isAnimatedAnimalsEnabled(this@ProfileActivity)
                            }
                        }
                    }
                } else {
                    showProfileNotFound()
                }
                // โหลดเสร็จ: ซ่อน ProgressBar และแสดง ScrollView
                binding.profileProgressBar.visibility = View.GONE
                binding.root.findViewById<android.widget.ScrollView>(R.id.scrollViewProfile)?.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                showError("เกิดข้อผิดพลาดในการโหลดข้อมูลโปรไฟล์: ${e.message}")
                // โหลดเสร็จ: ซ่อน ProgressBar และแสดง ScrollView
                binding.profileProgressBar.visibility = View.GONE
                binding.root.findViewById<android.widget.ScrollView>(R.id.scrollViewProfile)?.visibility = View.VISIBLE
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

    /**
     * โหลดพื้นหลังสำหรับผู้ใช้
     */
    private fun loadBackgroundForUser(userId: String) {
        val backgroundDrawable = backgroundManager.getBackgroundForUser(userId)
        binding.profileBackgroundImage.setImageDrawable(backgroundDrawable)
        
        // เริ่ม animation ทันที
        if (backgroundDrawable != null) {
            Log.d("ProfileActivity", "เริ่ม animation สำหรับพื้นหลัง")
            backgroundManager.startBackgroundAnimation(backgroundDrawable)
        }
        
        // แสดงชื่อสถานที่
        val locationName = backgroundManager.getLocationNameFromDrawable(backgroundDrawable)
        binding.locationNameText.text = "📍 $locationName"
        Log.d("ProfileActivity", "โหลดพื้นหลัง: $locationName")
    }

    /**
     * เปลี่ยนพื้นหลังใหม่
     */
    private fun changeBackground() {
        val currentUser = auth.currentUser ?: return
        Log.d("ProfileActivity", "เปลี่ยนพื้นหลัง - User ID: ${currentUser.uid}")
        
        val newBackgroundDrawable = backgroundManager.changeBackground(currentUser.uid)
        Log.d("ProfileActivity", "ได้พื้นหลังใหม่: ${newBackgroundDrawable != null}")
        
        if (newBackgroundDrawable != null) {
            binding.profileBackgroundImage.setImageDrawable(newBackgroundDrawable)
            
            // เริ่ม animation ทันที
            Log.d("ProfileActivity", "เริ่ม animation สำหรับพื้นหลังใหม่")
            backgroundManager.startBackgroundAnimation(newBackgroundDrawable)
            
            // แสดงชื่อสถานที่ใหม่
            val locationName = backgroundManager.getLocationNameFromDrawable(newBackgroundDrawable)
            binding.locationNameText.text = "📍 $locationName"
            Log.d("ProfileActivity", "ชื่อสถานที่ใหม่: $locationName")
            
            Toast.makeText(this, "เปลี่ยนพื้นหลังเป็น: $locationName", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("ProfileActivity", "ไม่สามารถโหลดพื้นหลังใหม่ได้")
            Toast.makeText(this, "เกิดข้อผิดพลาดในการเปลี่ยนพื้นหลัง", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // เริ่ม animation อีกครั้งเมื่อกลับมาที่หน้า
        targetUserId?.let { userId ->
            Log.d("ProfileActivity", "onResume - เริ่ม animation สำหรับ user: $userId")
            val backgroundDrawable = backgroundManager.getBackgroundForUser(userId)
            Log.d("ProfileActivity", "onResume - ได้ background drawable: ${backgroundDrawable != null}")
            if (backgroundDrawable != null) {
                backgroundManager.startBackgroundAnimation(backgroundDrawable)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // หยุด animation เมื่อออกจากหน้า
        targetUserId?.let { userId ->
            val backgroundDrawable = backgroundManager.getBackgroundForUser(userId)
            if (backgroundDrawable != null) {
                backgroundManager.stopBackgroundAnimation(backgroundDrawable)
            }
        }
    }
}

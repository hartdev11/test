package com.natthasethstudio.sethpos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

class StoreProfileActivity : AppCompatActivity() {
    private lateinit var storeImage: ImageView
    private lateinit var changeImageButton: FloatingActionButton
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    //private lateinit var inventoryButton: MaterialButton
    private lateinit var postsButton: MaterialButton
    private lateinit var dashboardButton: MaterialButton
    private lateinit var saveChangesButton: MaterialButton
    private lateinit var feedButton: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = result.data?.data
            selectedImageUri?.let {
                storeImage.setImageURI(it)
                uploadImageToFirebase(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // เพิ่มการตั้งค่า OpenGL
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(getColor(android.R.color.transparent))
        
        setContentView(R.layout.activity_store_profile)

        initializeViews()
        setupClickListeners()
        loadStoreData()
        updateStoreVerification()
        updateAllExistingStores()
    }

    private fun initializeViews() {
        storeImage = findViewById(R.id.storeImage)
        changeImageButton = findViewById(R.id.changeImageButton)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        //inventoryButton = findViewById(R.id.inventoryButton)
        postsButton = findViewById(R.id.postsButton)
        dashboardButton = findViewById(R.id.dashboardButton)
        saveChangesButton = findViewById(R.id.saveChangesButton)
        feedButton = findViewById(R.id.feedButton)
    }

    private fun setupClickListeners() {
        changeImageButton.setOnClickListener {
            openImagePicker()
        }

        //inventoryButton.setOnClickListener {
            // TODO: Navigate to inventory screen
            //Toast.makeText(this, "ไปยังหน้าคลังสินค้า", Toast.LENGTH_SHORT).show()
        //}

        postsButton.setOnClickListener {
            val intent = Intent(this, CreatePostActivity::class.java)
            startActivity(intent)
        }

        dashboardButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }

        feedButton.setOnClickListener {
            val intent = Intent(this, CustomerMainActivity::class.java)
            startActivity(intent)
        }

        saveChangesButton.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadStoreData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Set email from Firebase Auth
            emailInput.setText(currentUser.email)

            // Display placeholder for password (not the actual password)
            passwordInput.setText("********")

            // Load store data from Firestore
            db.collection("stores").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Load store image URL from Firestore
                        val storeImageUrl = document.getString("storeImage")
                        if (!storeImageUrl.isNullOrEmpty()) {
                            loadImageWithGlide(storeImageUrl)
                        } else {
                            // If no image URL in Firestore, try loading from Storage
                            val storeRef = storage.reference.child("store_images/${currentUser.uid}")
                            storeRef.downloadUrl
                                .addOnSuccessListener { uri ->
                                    // Save the URL to Firestore for future use
                                    db.collection("stores").document(currentUser.uid)
                                        .update("storeImage", uri.toString())
                                        .addOnSuccessListener {
                                            loadImageWithGlide(uri.toString())
                                        }
                                        .addOnFailureListener {
                                            handleImageLoadError()
                                        }
                                }
                                .addOnFailureListener {
                                    handleImageLoadError()
                                }
                        }
                    } else {
                        handleImageLoadError()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("StoreProfileActivity", "Error loading store data", e)
                    handleImageLoadError()
                }
        } else {
            // User is not logged in, handle accordingly
            Toast.makeText(this, "กรุณาเข้าสู่ระบบ", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadImageWithGlide(imageUrl: String) {
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.store_placeholder)
            .error(R.drawable.store_placeholder)
            .into(storeImage)
    }

    private fun handleImageLoadError() {
        storeImage.setImageResource(R.drawable.store_placeholder)
        Log.d("StoreProfileActivity", "No store image found or failed to load")
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        
        // ตรวจสอบประเภทไฟล์
        val mimeType = contentResolver.getType(imageUri)
        if (mimeType == null || !mimeType.startsWith("image/")) {
            Toast.makeText(this, "กรุณาเลือกไฟล์รูปภาพเท่านั้น", Toast.LENGTH_SHORT).show()
            return
        }

        // ตรวจสอบขนาดไฟล์
        val fileSize = contentResolver.openFileDescriptor(imageUri, "r")?.statSize ?: 0
        val maxSize = 5 * 1024 * 1024 // 5MB
        if (fileSize > maxSize) {
            Toast.makeText(this, "ขนาดไฟล์ต้องไม่เกิน 5MB", Toast.LENGTH_SHORT).show()
            return
        }

        val storeRef = storage.reference.child("store_images/${currentUser.uid}")
        
        // Show loading state
        storeImage.setImageResource(R.drawable.store_placeholder)
        
        storeRef.putFile(imageUri)
            .addOnSuccessListener {
                // Get download URL after successful upload
                storeRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firestore with the new image URL
                    db.collection("stores").document(currentUser.uid)
                        .update("storeImage", downloadUri.toString())
                        .addOnSuccessListener {
                            loadImageWithGlide(downloadUri.toString())
                            Toast.makeText(this, "อัพโหลดรูปภาพสำเร็จ", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "อัพเดท URL รูปภาพไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
                            handleImageLoadError()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "อัพโหลดรูปภาพไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
                handleImageLoadError()
            }
    }

    private fun saveChanges() {
        val email = emailInput.text.toString()
        val password = passwordInput.text.toString()

        if (email.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกอีเมล", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser ?: return

        // Create a map of updates
        val updates = mutableMapOf<String, Any>()

        // Update email if changed
        if (email != currentUser.email) {
            // Re-authenticate user before updating email
            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(currentUser.email!!, password)
            
            currentUser.reauthenticate(credential)
                .addOnSuccessListener {
                    // After successful re-authentication, update email
                    @Suppress("DEPRECATION")
                    currentUser.updateEmail(email)
                        .addOnSuccessListener {
                            updates["email"] = email
                            // Update Firestore after successful email update
                            updateFirestore(updates)
                        }
                        .addOnFailureListener { e: Exception ->
                            Toast.makeText(this, "อัพเดทอีเมลไม่สำเร็จ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e: Exception ->
                    Toast.makeText(this, "ยืนยันตัวตนไม่สำเร็จ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // If no email change, just update Firestore
            updateFirestore(updates)
        }
    }

    private fun updateFirestore(updates: Map<String, Any>) {
        val currentUser = auth.currentUser ?: return
        
        // Update both users and stores collections
        val batch = db.batch()
        
        // Update users collection
        val userRef = db.collection("users").document(currentUser.uid)
        batch.update(userRef, updates)
        
        // Update stores collection
        val storeRef = db.collection("stores").document(currentUser.uid)
        batch.update(storeRef, updates)
        
        // Commit the batch
        batch.commit()
                .addOnSuccessListener {
                Toast.makeText(this, "บันทึกการเปลี่ยนแปลงสำเร็จ", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                Toast.makeText(this, "บันทึกการเปลี่ยนแปลงไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStoreVerification() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // อัพเดทข้อมูลในคอลเลกชัน users
            db.collection("users").document(currentUser.uid)
                .update("isStore", true)
                .addOnSuccessListener {
                    Log.d("StoreProfileActivity", "Updated isStore in users collection")
                }
                .addOnFailureListener { e ->
                    Log.e("StoreProfileActivity", "Error updating isStore in users collection", e)
                }

            // อัพเดทข้อมูลในคอลเลกชัน stores
            db.collection("stores").document(currentUser.uid)
                .update("isStore", true)
                .addOnSuccessListener {
                    Log.d("StoreProfileActivity", "Updated isStore in stores collection")
                }
                .addOnFailureListener { e ->
                    Log.e("StoreProfileActivity", "Error updating isStore in stores collection", e)
                }
        }
    }

    private fun updateAllExistingStores() {
        // อัพเดทข้อมูลในคอลเลกชัน stores
        db.collection("stores")
            .get()
            .addOnSuccessListener { documents ->
                val storeUpdates = mutableListOf<Task<Void>>()
                
                for (document in documents) {
                    val storeRef = db.collection("stores").document(document.id)
                    val userRef = db.collection("users").document(document.id)
                    
                    // สร้าง batch สำหรับแต่ละ store
                    val batch = db.batch()
                    batch.update(storeRef, "isStore", true)
                    
                    // ตรวจสอบและอัพเดทข้อมูลในคอลเลกชัน users
                    val userUpdateTask = userRef.get()
                        .continueWithTask { task ->
                            if (task.isSuccessful) {
                                val userDoc = task.result
                                if (userDoc.exists()) {
                                    // ถ้ามี document อยู่แล้ว ให้อัพเดท
                                    batch.update(userRef, "isStore", true)
                                } else {
                                    // ถ้าไม่มี document ให้สร้างใหม่
                                    val userData = mapOf(
                                        "isStore" to true,
                                        "storeName" to (document.getString("storeName") ?: ""),
                                        "storeImage" to (document.getString("storeImage") ?: "")
                                    )
                                    batch.set(userRef, userData, SetOptions.merge())
                                }
                                batch.commit()
                            } else {
                                throw task.exception ?: Exception("Unknown error")
                            }
                        }
                    
                    storeUpdates.add(userUpdateTask)
                }
                
                // รอให้ทุกการอัพเดทเสร็จสิ้น
                Tasks.whenAll(storeUpdates)
                    .addOnSuccessListener {
                        Log.d("StoreProfileActivity", "Successfully updated all existing stores")
                    }
                    .addOnFailureListener { e ->
                        Log.e("StoreProfileActivity", "Error updating existing stores", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("StoreProfileActivity", "Error getting stores collection", e)
            }
    }
} 
package com.natthasethstudio.sethpos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.natthasethstudio.sethpos.databinding.ActivityAddMenuBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

import java.util.*

class AddMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMenuBinding
    private lateinit var nameEditText: EditText
    private lateinit var priceEditText: EditText
    private lateinit var imageView: ImageView
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var categoryEditText: EditText
    private lateinit var imageUrlEditText: EditText
    private lateinit var buttonSelectImage: Button
    private lateinit var toolbar: Toolbar

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var rewardedAd: RewardedAd? = null

    companion object {
        const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 1001
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            imageView.setImageURI(it)
            imageUrlEditText.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = binding.toolbarAddMenu
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "เพิ่มเมนูใหม่"

        nameEditText = binding.editTextName
        priceEditText = binding.editTextPrice
        imageView = binding.imageView
        btnSave = binding.buttonSave
        progressBar = binding.progressBarAdd
        categoryEditText = binding.editTextCategory
        imageUrlEditText = binding.editTextImageUrl
        buttonSelectImage = binding.buttonSelectImage

        imageView.setOnClickListener {
            checkPermissionAndPickImage()
        }

        buttonSelectImage.setOnClickListener {
            checkPermissionAndPickImage()
        }

        btnSave.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val priceText = priceEditText.text.toString().trim()
            val category = categoryEditText.text.toString().trim()
            val imageUrlText = imageUrlEditText.text.toString().trim()

            if (name.isEmpty() || priceText.isEmpty() || category.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกชื่อ ราคา และหมวดหมู่", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceText.toDoubleOrNull()
            if (price == null) {
                Toast.makeText(this, "กรอกราคาที่ถูกต้อง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE

            if (imageUri != null) {
                uploadImageAndSaveMenu(name, price, category, imageUri!!)
            } else if (imageUrlText.isNotEmpty()) {
                saveMenu(name, price, category, imageUrlText)
            } else {
                saveMenu(name, price, category, null)
            }
        }

        // Setup back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Initialize AdMob
        MobileAds.initialize(this)
        loadRewardedAd()
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

    private fun uploadImageAndSaveMenu(name: String, price: Double, category: String, uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "กรุณาล็อกอินก่อนเพิ่มเมนู", Toast.LENGTH_SHORT).show()
            progressBar.visibility = ProgressBar.GONE
            return
        }

        val storageRef = FirebaseStorage.getInstance().reference
        val fileName = "${user.uid}_${UUID.randomUUID()}"
        val imageRef: StorageReference = storageRef.child("menu_images/$fileName")

        // สร้าง metadata สำหรับรูปภาพ
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/*")
            .setCustomMetadata("userId", user.uid)
            .build()

        imageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveMenu(name, price, category, downloadUri.toString())
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการดึง URL รูปภาพ: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
            }
            .addOnFailureListener { e ->
                val errorMessage = when {
                    e.message?.contains("Permission denied") == true -> "ไม่มีสิทธิ์ในการอัพโหลดรูปภาพ กรุณาตรวจสอบการล็อกอิน"
                    else -> "อัปโหลดรูปภาพล้มเหลว: ${e.message}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                progressBar.visibility = ProgressBar.GONE
            }
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                progressBar.progress = progress
            }
    }

    private fun saveMenu(name: String, price: Double, category: String, imageUrl: String?) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "กรุณาล็อกอินก่อนเพิ่มเมนู", Toast.LENGTH_SHORT).show()
            progressBar.visibility = ProgressBar.GONE
            return
        }

        val storeId = intent.getStringExtra("storeId") ?: ""

        // Check subscription status and menu limit
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val isPremium = userDoc.getBoolean("isPremium") ?: false
                
                if (!isPremium) {
                    // Check current menu count for non-premium users
                    db.collection("menu_items")
                        .whereEqualTo("userId", user.uid)
                        .get()
                        .addOnSuccessListener { menuSnapshot ->
                            if (menuSnapshot.size() >= 25) {
                                // Show dialog to watch ad
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("เพิ่มเมนูเกินขีดจำกัด")
                                    .setMessage("คุณเพิ่มเมนูได้สูงสุด 25 รายการแล้ว\nต้องการดูโฆษณาเพื่อเพิ่มเมนูอีก 5 รายการหรือไม่?")
                                    .setPositiveButton("ดูโฆษณา") { dialog, _ ->
                                        showRewardedAd(name, price, category, imageUrl, user.uid, storeId)
                                    }
                                    .setNegativeButton("ยกเลิก") { dialog, _ ->
                                        dialog.dismiss()
                                        progressBar.visibility = ProgressBar.GONE
                                    }
                                    .show()
                                return@addOnSuccessListener
                            }
                            saveMenuItem(name, price, category, imageUrl, user.uid, storeId)
                        }
                } else {
                    // Premium users can add unlimited menu items
                    saveMenuItem(name, price, category, imageUrl, user.uid, storeId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "เกิดข้อผิดพลาดในการตรวจสอบสถานะสมาชิก", Toast.LENGTH_SHORT).show()
                progressBar.visibility = ProgressBar.GONE
            }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-6439629123336863/6852255281", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AdMob", "Ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdMob", "Ad loaded successfully")
                    rewardedAd = ad
                }
            })
    }

    private fun showRewardedAd(name: String, price: Double, category: String, imageUrl: String?, userId: String, storeId: String) {
        if (rewardedAd == null) {
            Toast.makeText(this, "กำลังโหลดโฆษณา กรุณาลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
            return
        }

        rewardedAd?.show(this) { rewardItem ->
            // After watching ad, increase menu limit by 5
            db.collection("users").document(userId)
                .update("menuLimit", 30)
                .addOnSuccessListener {
                    saveMenuItem(name, price, category, imageUrl, userId, storeId)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "เกิดข้อผิดพลาดในการเพิ่มขีดจำกัดเมนู", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
        }
    }

    private fun saveMenuItem(name: String, price: Double, category: String, imageUrl: String?, userId: String, storeId: String) {
        val menuItem = hashMapOf<String, Any>(
            "name" to name,
            "price" to price,
            "category" to category,
            "userId" to userId,
            "storeId" to storeId
        )
        if (!imageUrl.isNullOrEmpty()) {
            menuItem["imageUrl"] = imageUrl
        }

        Log.d("AddMenuActivity", "Saving menu item: $menuItem")
        
        db.collection("menu_items")
            .add(menuItem)
            .addOnSuccessListener { documentReference ->
                Log.d("AddMenuActivity", "Menu item saved successfully with ID: ${documentReference.id}")
                Toast.makeText(this, "เพิ่มเมนูเรียบร้อย", Toast.LENGTH_SHORT).show()
                // ไม่ finish activity ทันที เพื่อให้หน้าแสดงรายการเมนูอัพเดทโดยอัตโนมัติ
                Handler(Looper.getMainLooper()).postDelayed({
                    setResult(RESULT_OK, Intent())
                    finish()
                }, 1000) // ปรับเวลาเป็น 1 วินาทีเพื่อให้แน่ใจว่าข้อมูลอัพเดท
            }
            .addOnFailureListener { e ->
                Log.e("AddMenuActivity", "Error saving menu item: ${e.message}")
                Toast.makeText(this, "เกิดข้อผิดพลาดในการเพิ่มเมนู: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBar.visibility = ProgressBar.GONE
            }
    }

    private fun checkPermissionAndPickImage() {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }

        if (permission != null && ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_READ_EXTERNAL_STORAGE)
        } else {
            openFileChooser()
        }
    }

    private fun openFileChooser() {
        pickImageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
        ))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ_EXTERNAL_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFileChooser()
        } else {
            Toast.makeText(this, "กรุณาอนุญาตสิทธิ์เพื่อเลือกภาพ", Toast.LENGTH_SHORT).show()
        }
    }
}

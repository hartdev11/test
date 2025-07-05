package com.natthasethstudio.sethpos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Geocoder
import android.location.Location
import android.location.Address

class CreatePostActivity : AppCompatActivity() {

    private lateinit var editTextPostBody: EditText
    private lateinit var buttonPost: Button
    private lateinit var toolbarCreatePost: Toolbar
    private lateinit var imageViewPreview: ImageView
    private lateinit var buttonSelectImage: Button
    private lateinit var textViewImageLocked: TextView
    private lateinit var buttonWatchAd: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid
    private val currentUser = auth.currentUser

    private var editingPostId: String? = null
    private var imageUri: Uri? = null
    private var isPremiumUser: Boolean = false
    private var dailyPostCount: Int = 0
    private var rewardedAd: RewardedAd? = null
    private var isImageUnlocked: Boolean = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentProvince: String? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 2001
    private var post: Post? = null

    private val pickImageLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                if (isPremiumUser || isImageUnlocked) {
                    imageUri = uri
                    imageViewPreview.setImageURI(uri)
                    imageViewPreview.visibility = View.VISIBLE
                    if (!isPremiumUser) {
                        isImageUnlocked = false
                        textViewImageLocked.visibility = View.VISIBLE
                        buttonWatchAd.visibility = View.VISIBLE
                        buttonSelectImage.isEnabled = false
                        Toast.makeText(this, "หากต้องการอัปโหลดรูปภาพเพิ่มเติม กรุณาดูโฆษณาอีกครั้ง", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.image_upload_locked), Toast.LENGTH_SHORT).show()
                }
            } else {
                imageViewPreview.visibility = View.GONE
                imageUri = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        // Initialize AdMob
        MobileAds.initialize(this)

        editTextPostBody = findViewById(R.id.editTextPostBody)
        buttonPost = findViewById(R.id.buttonPost)
        toolbarCreatePost = findViewById(R.id.toolbarCreatePost)
        imageViewPreview = findViewById(R.id.imageViewPreview)
        buttonSelectImage = findViewById(R.id.buttonSelectImage)
        textViewImageLocked = findViewById(R.id.textViewImageLocked)
        buttonWatchAd = findViewById(R.id.buttonWatchAd)

        setSupportActionBar(toolbarCreatePost)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbarCreatePost.setNavigationOnClickListener { finish() }

        editingPostId = intent.getStringExtra("postId")
        if (editingPostId != null) {
            buttonPost.text = getString(R.string.save_button_label)
            loadPostForEditing(editingPostId!!)
        } else {
            buttonPost.text = getString(R.string.post)
            checkPremiumStatus()
            checkDailyPostCount()
            loadRewardedAd()
        }

        buttonPost.setOnClickListener {
            val postText = editTextPostBody.text.toString().trim()

            if (postText.isNotEmpty() || imageUri != null) {
                if (editingPostId != null) {
                    updatePost(editingPostId!!, postText, imageUri)
                } else {
                    if (isPremiumUser || dailyPostCount < 5) {
                        createNewPost(postText, imageUri)
                    } else {
                        Toast.makeText(this, "คุณได้โพสต์ครบ 5 โพสต์ต่อวันแล้ว กรุณาสมัครสมาชิกพรีเมียมเพื่อโพสต์ได้ไม่จำกัด", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "กรุณาเขียนข้อความหรือเพิ่มรูปภาพก่อนโพสต์", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSelectImage.setOnClickListener {
            if (isPremiumUser || isImageUnlocked) {
                pickImageLauncher.launch("image/*")
            } else {
                Toast.makeText(this, getString(R.string.image_upload_locked), Toast.LENGTH_SHORT).show()
            }
        }

        buttonWatchAd.setOnClickListener {
            showRewardedAd()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocationAndSetProvince()
    }

    private fun requestLocationAndSetProvince() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                getProvinceFromLocation(location)
            } else {
                // Could optionally request a fresh location update here if lastLocation is null
                Log.w("CreatePostActivity", "Last known location is null.")
                Toast.makeText(this, "ไม่สามารถดึงตำแหน่งล่าสุดได้", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Log.e("CreatePostActivity", "Error getting location", it)
            Toast.makeText(this, "เกิดข้อผิดพลาดในการดึงตำแหน่ง", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getProvinceFromLocation(location: Location) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        currentProvince = addresses[0].adminArea
                        Log.d("CreatePostActivity", "Province set to: $currentProvince")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    currentProvince = addresses[0].adminArea
                    Log.d("CreatePostActivity", "Province set to: $currentProvince")
                }
            }
        } catch (e: Exception) {
            Log.e("CreatePostActivity", "Error getting province from location", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                requestLocationAndSetProvince()
            } else {
                Toast.makeText(this, "ไม่ได้รับอนุญาตให้เข้าถึงตำแหน่ง จังหวัดจะไม่ถูกบันทึกในโพสต์", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-6439629123336863/9446575132", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AdMob", "Ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    buttonWatchAd.isEnabled = false
                    Toast.makeText(this@CreatePostActivity, getString(R.string.ad_failed), Toast.LENGTH_SHORT).show()
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdMob", "Ad loaded successfully")
                    rewardedAd = ad
                    buttonWatchAd.isEnabled = true
                }
            })
    }

    private fun showRewardedAd() {
        if (rewardedAd == null) {
            Toast.makeText(this, getString(R.string.ad_loading), Toast.LENGTH_SHORT).show()
            loadRewardedAd()
            return
        }

        rewardedAd?.show(this) { rewardItem ->
            isImageUnlocked = true
            buttonSelectImage.isEnabled = true
            textViewImageLocked.visibility = View.GONE
            buttonWatchAd.visibility = View.GONE
            Toast.makeText(this, getString(R.string.ad_rewarded), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPremiumStatus() {
        currentUserId?.let { userId ->
            firestore.collection("premium_users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    isPremiumUser = document.exists() && (document.getBoolean("isPremium") ?: false)
                    if (!isPremiumUser) {
                        textViewImageLocked.visibility = View.VISIBLE
                        buttonWatchAd.visibility = View.VISIBLE
                        buttonSelectImage.isEnabled = false
                    } else {
                        textViewImageLocked.visibility = View.GONE
                        buttonWatchAd.visibility = View.GONE
                        buttonSelectImage.isEnabled = true
                    }
                }
        }
    }

    private fun checkDailyPostCount() {
        currentUserId?.let { userId ->
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("postTime", "$today 00:00:00")
                .whereLessThanOrEqualTo("postTime", "$today 23:59:59")
                .get()
                .addOnSuccessListener { documents ->
                    dailyPostCount = documents.size()
                }
        }
    }

    private fun loadPostForEditing(postId: String) {
        firestore.collection("posts").document(postId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    this.post = document.toObject(Post::class.java)
                    post?.let {
                        editTextPostBody.setText(it.postText)
                        val imageUrl = it.postImageUrl
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this).load(imageUrl).into(imageViewPreview)
                            imageViewPreview.visibility = View.VISIBLE
                            imageUri = imageUrl.toUri()
                        }
                    }
                } else {
                    Toast.makeText(this, "ไม่พบโพสต์ที่ต้องการแก้ไข", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "เกิดข้อผิดพลาดในการโหลดโพสต์", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun resizeAndCompressImage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val maxWidth = 1080
            val aspectRatio = originalBitmap.height.toDouble() / originalBitmap.width
            val newHeight = (maxWidth * aspectRatio).toInt()

            val resizedBitmap = originalBitmap.scale(maxWidth, newHeight, filter = true)

            val file = File(cacheDir, "resized_image_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            Log.e("ImageResize", "Error resizing image: ${e.message}")
            null
        }
    }

    private fun createNewPost(postText: String, imageUri: Uri?) {
        try {
            currentUserId?.let { userId ->
                currentUser?.let { user ->
                    Log.d("CreatePostActivity", "Current user: ${user.displayName}, email: ${user.email}, uid: ${user.uid}")
                    
                    if (!isNetworkAvailable()) {
                        Toast.makeText(this, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Validate post text
                    if (postText.length > 1000) {
                        Toast.makeText(this, "ข้อความต้องไม่เกิน 1000 ตัวอักษร", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Show loading
                    buttonPost.isEnabled = false
                    buttonPost.text = "กำลังโพสต์..."

                    firestore.collection("users").document(userId)
                        .get()
                        .addOnSuccessListener { documentSnapshot ->
                            try {
                                Log.d("CreatePostActivity", "Fetching user data for userId: $userId")
                                Log.d("CreatePostActivity", "Document exists: ${documentSnapshot.exists()}")
                                
                                if (!documentSnapshot.exists()) {
                                    Log.e("CreatePostActivity", "User document does not exist for userId: $userId")
                                    Log.d("CreatePostActivity", "Creating user document for userId: $userId")
                                    
                                    // สร้าง user document ใหม่
                                    val userData = mapOf(
                                        "userId" to userId,
                                        "displayName" to (user.displayName ?: "User"),
                                        "email" to (user.email ?: ""),
                                        "avatarId" to 0,
                                        "role" to "customer",
                                        "createdAt" to com.google.firebase.Timestamp.now()
                                    )
                                    
                                    firestore.collection("users").document(userId)
                                        .set(userData)
                                        .addOnSuccessListener {
                                            Log.d("CreatePostActivity", "User document created successfully")
                                            // เรียกใช้ฟังก์ชันเดิมอีกครั้งหลังจากสร้าง document
                                            createNewPost(postText, imageUri)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("CreatePostActivity", "Error creating user document: ${e.message}")
                                            handleError("เกิดข้อผิดพลาดในการสร้างข้อมูลผู้ใช้: ${e.message}")
                                        }
                                    return@addOnSuccessListener
                                }

                                Log.d("CreatePostActivity", "Document data: ${documentSnapshot.data}")
                                
                                val avatarId = documentSnapshot.getLong("avatarId")?.toInt() ?: 0
                                val displayName = documentSnapshot.getString("displayName")
                                val role = documentSnapshot.getString("role")

                                Log.d("CreatePostActivity", "User data - displayName: $displayName, role: $role")
                                
                                // ตรวจสอบ field names ที่มีใน document
                                documentSnapshot.data?.keys?.forEach { key ->
                                    Log.d("CreatePostActivity", "Field: $key = ${documentSnapshot.get(key)}")
                                }

                                // ลองหาชื่อผู้ใช้จาก field names อื่นๆ ถ้า displayName เป็น null
                                var finalDisplayName = displayName
                                if (finalDisplayName.isNullOrEmpty()) {
                                    finalDisplayName = documentSnapshot.getString("name") ?: 
                                                     documentSnapshot.getString("username") ?: 
                                                     documentSnapshot.getString("nickname") ?:
                                                     documentSnapshot.getString("fullName")
                                    
                                    Log.d("CreatePostActivity", "Tried fallback names - finalDisplayName: $finalDisplayName")
                                }

                                if (finalDisplayName.isNullOrEmpty()) {
                                    Log.e("CreatePostActivity", "Display name is null or empty. Available fields: ${documentSnapshot.data?.keys}")
                                    handleError("ไม่พบชื่อผู้ใช้ กรุณาตั้งชื่อในโปรไฟล์")
                                    return@addOnSuccessListener
                                }

                                if (imageUri != null) {
                                    // Validate image size
                                    val imageFile = File(imageUri.path!!)
                                    if (imageFile.length() > 5 * 1024 * 1024) { // 5MB limit
                                        Toast.makeText(this, "รูปภาพต้องมีขนาดไม่เกิน 5MB", Toast.LENGTH_SHORT).show()
                                        buttonPost.isEnabled = true
                                        buttonPost.text = getString(R.string.post)
                                        return@addOnSuccessListener
                                    }

                                    val resizedFile = resizeAndCompressImage(imageUri)
                                    if (resizedFile != null) {
                                        val fileUri = Uri.fromFile(resizedFile)
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("post_images/${System.currentTimeMillis()}_${fileUri.lastPathSegment}")

                                        // Add security rules
                                        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                                            .setContentType("image/jpeg")
                                            .build()

                                        storageRef.putFile(fileUri, metadata)
                                            .addOnProgressListener { taskSnapshot ->
                                                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                                                buttonPost.text = "กำลังอัพโหลด... $progress%"
                                            }
                                            .addOnSuccessListener { taskSnapshot ->
                                                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                                                    try {
                                                        val postTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                                        val post = Post(
                                                            userId = userId,
                                                            displayName = finalDisplayName,
                                                            profileImageUrl = user.photoUrl?.toString(),
                                                            postTime = postTime,
                                                            postText = postText,
                                                            postImageUrl = downloadUri.toString(),
                                                            avatarId = avatarId,
                                                            province = currentProvince
                                                        )
                                                        checkLocationPermissionAndFetchProvince {
                                                            savePostToFirestore(post, currentProvince)
                                                        }
                                                    } catch (e: Exception) {
                                                        handleError("เกิดข้อผิดพลาดในการสร้างโพสต์: ${e.message}")
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                handleError("อัปโหลดรูปไม่สำเร็จ: ${e.message}")
                                            }
                                    } else {
                                        handleError("เกิดข้อผิดพลาดในการประมวลผลรูปภาพ")
                                    }
                                } else {
                                    try {
                                        val postTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                        val post = Post(
                                            userId = userId,
                                            displayName = finalDisplayName,
                                            profileImageUrl = user.photoUrl?.toString(),
                                            postTime = postTime,
                                            postText = postText,
                                            avatarId = avatarId,
                                            province = currentProvince
                                        )
                                        checkLocationPermissionAndFetchProvince {
                                            savePostToFirestore(post, currentProvince)
                                        }
                                    } catch (e: Exception) {
                                        handleError("เกิดข้อผิดพลาดในการสร้างโพสต์: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                handleError("เกิดข้อผิดพลาดในการสร้างโพสต์: ${e.message}")
                            }
                        }
                        .addOnFailureListener { e ->
                            handleError("เกิดข้อผิดพลาดในการโหลดข้อมูลผู้ใช้: ${e.message}")
                        }
                }
            }
        } catch (e: Exception) {
            handleError("เกิดข้อผิดพลาดในการสร้างโพสต์: ${e.message}")
        }
    }

    private fun checkLocationPermissionAndFetchProvince(onProvinceReady: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            Toast.makeText(this, "กรุณาอนุญาตการเข้าถึงตำแหน่งเพื่อบันทึกจังหวัดในโพสต์", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: List<Address>) {
                                handleProvinceResult(addresses, onProvinceReady)
                            }
                            override fun onError(errorMessage: String?) {
                                onProvinceReady()
                            }
                        }
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    handleProvinceResult(addresses, onProvinceReady)
                }
            } else {
                onProvinceReady() // fallback ถ้าดึงไม่ได้
            }
        }.addOnFailureListener {
            onProvinceReady() // fallback ถ้าดึงไม่ได้
        }
    }

    private fun handleProvinceResult(addresses: List<Address>?, onProvinceReady: () -> Unit) {
        if (!addresses.isNullOrEmpty()) {
            val province = addresses[0].adminArea
            Log.d("CreatePostActivity", "Raw province from geocoder: $province")
            if (!province.isNullOrEmpty()) {
                currentProvince = province
                Log.d("CreatePostActivity", "Set currentProvince to: $currentProvince")
            } else {
                Log.w("CreatePostActivity", "Province is null or empty from geocoder")
            }
        } else {
            Log.w("CreatePostActivity", "No addresses found from geocoder")
        }
        onProvinceReady()
    }

    private fun savePostToFirestore(post: Post, province: String? = null) {
        try {
            val postMap = postToMap(post)
            if (province != null) {
                postMap["province"] = province
            }
            
            Log.d("CreatePostActivity", "Saving post with province: ${postMap["province"]}")
            
            firestore.collection("posts").add(postMap)
                .addOnSuccessListener { documentReference ->
                    Log.d("CreatePostActivity", "Post saved successfully with ID: ${documentReference.id}")
                    Toast.makeText(this, "โพสต์สำเร็จ", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("CreatePostActivity", "Error saving post: ${e.message}")
                    handleError("เกิดข้อผิดพลาดในการบันทึกโพสต์: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("CreatePostActivity", "Error in savePostToFirestore: ${e.message}")
            handleError("เกิดข้อผิดพลาดในการบันทึกโพสต์: ${e.message}")
        }
    }

    private fun postToMap(post: Post): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["userId"] = post.userId
        map["displayName"] = post.displayName
        map["profileImageUrl"] = post.profileImageUrl
        map["postTime"] = post.postTime
        map["postText"] = post.postText
        map["postImageUrl"] = post.postImageUrl
        map["avatarId"] = post.avatarId
        map["province"] = post.province
        return map
    }

    private fun handleError(message: String) {
        Log.e("CreatePostActivity", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        buttonPost.isEnabled = true
        buttonPost.text = getString(R.string.post)
    }

    private fun isNetworkAvailable(): Boolean {
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
    }

    private fun updatePost(postId: String, postText: String, imageUri: Uri?) {
        val postUpdate = mutableMapOf<String, Any>(
            "postText" to postText
        )
        if(currentProvince != null){
            postUpdate["province"] = currentProvince!!
        }

                                val currentImageUrl = post?.postImageUrl
                        if (imageUri != null && imageUri.toString() != currentImageUrl) {
            uploadImageAndUpdatePost(postId, imageUri, postUpdate)
        } else {
            firestore.collection("posts").document(postId)
                .update(postUpdate)
                .addOnSuccessListener {
                    Toast.makeText(this, "บันทึกการเปลี่ยนแปลงเรียบร้อย", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun uploadImageAndUpdatePost(postId: String, imageUri: Uri?, postUpdate: Map<String, Any>) {
        val storageRef = FirebaseStorage.getInstance().reference.child("post_images/${System.currentTimeMillis()}_${imageUri?.lastPathSegment}")
        val uploadTask = storageRef.putFile(imageUri!!)

        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnSuccessListener { downloadUrl ->
            val updatedData = postUpdate + mapOf("postImageUrl" to downloadUrl.toString())
            firestore.collection("posts").document(postId).update(updatedData)
                .addOnSuccessListener {
                    Toast.makeText(this, "บันทึกการเปลี่ยนแปลงเรียบร้อย", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "อัปโหลดรูปไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

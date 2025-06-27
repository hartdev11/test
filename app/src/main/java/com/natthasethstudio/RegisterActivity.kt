package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var merchantNameEditText: EditText
    private lateinit var nicknameEditText: EditText
    private lateinit var loginTextView: TextView
    private lateinit var avatarContainer: LinearLayout
    private var selectedAvatarId: Int = 0

    private val avatarResources = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)
        roleRadioGroup = findViewById(R.id.roleRadioGroup)
        merchantNameEditText = findViewById(R.id.merchantNameEditText)
        nicknameEditText = findViewById(R.id.nicknameEditText)
        loginTextView = findViewById(R.id.loginTextView)
        avatarContainer = findViewById(R.id.avatarContainer)

        setupAvatarSelection()

        // แสดง/ซ่อนช่องกรอกชื่อร้านค้าและชื่อเล่นเมื่อเลือกบทบาท
        roleRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioMerchant) {
                merchantNameEditText.visibility = View.VISIBLE
                nicknameEditText.visibility = View.GONE
            } else if (checkedId == R.id.radioCustomer) {
                merchantNameEditText.visibility = View.GONE
                nicknameEditText.visibility = View.VISIBLE
            } else {
                 merchantNameEditText.visibility = View.GONE
                 nicknameEditText.visibility = View.GONE
            }
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val selectedRoleId = roleRadioGroup.checkedRadioButtonId
            val selectedRole = when (selectedRoleId) {
                R.id.radioMerchant -> "merchant"
                R.id.radioCustomer -> "customer"
                else -> "customer"
            }
            val merchantName = merchantNameEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกอีเมลและรหัสผ่านให้ครบ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "รูปแบบอีเมลไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedRole == "merchant" && merchantName.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกชื่อร้านค้า", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
             if (selectedRole == "customer" && nickname.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกชื่อเล่น", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        val userMap = hashMapOf(
                            "email" to email,
                            "role" to selectedRole,
                            "avatarId" to selectedAvatarId,
                            "isStore" to (selectedRole == "merchant")
                        )
                        if (selectedRole == "merchant") {
                            userMap["merchantName"] = merchantName
                        } else if (selectedRole == "customer") {
                            userMap["nickname"] = nickname
                        }

                        // บันทึกข้อมูล user
                        db.collection("users").document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                if (selectedRole == "merchant") {
                                    val storeMap = hashMapOf(
                                        "storeId" to userId,
                                        "storeName" to merchantName,
                                        "storeCategory" to "",
                                        "storeImage" to "",
                                        "isStore" to true
                                    )
                                    db.collection("stores").document(userId)
                                        .set(storeMap)
                                        .addOnSuccessListener {
                                            progressBar.visibility = View.GONE
                                            Toast.makeText(this, "สมัครสมาชิกสำเร็จ", Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            progressBar.visibility = View.GONE
                                            Toast.makeText(this, "บันทึกร้านค้าล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "สมัครสมาชิกสำเร็จ", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "บันทึกข้อมูลผู้ใช้ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        progressBar.visibility = View.GONE
                        val exception = task.exception
                        if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "อีเมลนี้ถูกใช้งานแล้ว กรุณาเข้าสู่ระบบหรือใช้อีเมลอื่น", Toast.LENGTH_LONG).show()
                        } else if (exception is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, "รูปแบบอีเมลไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, exception?.localizedMessage ?: "สมัครสมาชิกล้มเหลว", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }

        loginTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupAvatarSelection() {
        avatarResources.forEachIndexed { index, resourceId ->
            val avatarView = layoutInflater.inflate(R.layout.item_avatar, avatarContainer, false)
            val avatarImage = avatarView.findViewById<ImageView>(R.id.avatarImage)
            val selectedIndicator = avatarView.findViewById<ImageView>(R.id.selectedIndicator)

            avatarImage.setImageResource(resourceId)
            avatarView.setOnClickListener {
                // Reset all indicators
                for (i in 0 until avatarContainer.childCount) {
                    val child = avatarContainer.getChildAt(i)
                    child.findViewById<ImageView>(R.id.selectedIndicator).visibility = View.GONE
                }
                // Show selected indicator
                selectedIndicator.visibility = View.VISIBLE
                selectedAvatarId = index
            }

            avatarContainer.addView(avatarView)
        }
    }
}
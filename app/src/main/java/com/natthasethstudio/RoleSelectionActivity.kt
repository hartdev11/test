package com.natthasethstudio.sethpos

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: View

    private var selectedRole: String? = null

    private lateinit var merchantCard: CardView
    private lateinit var customerCard: CardView
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        merchantCard = findViewById(R.id.merchantCard)
        customerCard = findViewById(R.id.customerCard)
        progressBar = findViewById(R.id.progressBar)
        continueButton = findViewById(R.id.continueButton)

        merchantCard.setOnClickListener {
            selectedRole = "merchant"
            updateSelectionUI()
        }

        customerCard.setOnClickListener {
            selectedRole = "customer"
            updateSelectionUI()
        }

        continueButton.setOnClickListener {
            if (selectedRole == null) {
                Toast.makeText(this, "กรุณาเลือกบทบาทก่อนดำเนินการต่อ", Toast.LENGTH_SHORT).show()
            } else {
                if (selectedRole == "merchant") {
                    showStoreNameDialog()
                } else {
                    showNicknameDialog()
                }
            }
        }

        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        if (selectedRole == "merchant") {
            merchantCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.merchant_gradient_start))
            customerCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
        } else if (selectedRole == "customer") {
            customerCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.customer_gradient_start))
            merchantCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
        } else {
            merchantCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
            customerCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
        }

        // ✅ Enable/Disable continue button based on selection
        continueButton.isEnabled = selectedRole != null
        continueButton.alpha = if (selectedRole != null) 1.0f else 0.4f
    }

    private fun showStoreNameDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_store_name)

        val titleText = dialog.findViewById<TextView>(R.id.dialogTitle)
        val inputField = dialog.findViewById<EditText>(R.id.inputField)
        val confirmButton = dialog.findViewById<Button>(R.id.confirmButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)

        titleText.text = "กรอกชื่อร้านค้า"
        inputField.hint = "ชื่อร้านค้า"

        confirmButton.setOnClickListener {
            val storeName = inputField.text.toString().trim()
            if (storeName.isNotEmpty()) {
                dialog.dismiss()
                saveUserRole("merchant", storeName)
            } else {
                Toast.makeText(this, "กรุณากรอกชื่อร้านค้า", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showNicknameDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_store_name)

        val titleText = dialog.findViewById<TextView>(R.id.dialogTitle)
        val inputField = dialog.findViewById<EditText>(R.id.inputField)
        val confirmButton = dialog.findViewById<Button>(R.id.confirmButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)

        titleText.text = "กรอกชื่อเล่น"
        inputField.hint = "ชื่อเล่น"

        confirmButton.setOnClickListener {
            val nickname = inputField.text.toString().trim()
            if (nickname.isNotEmpty()) {
                dialog.dismiss()
                saveUserRole("customer", nickname)
            } else {
                Toast.makeText(this, "กรุณากรอกชื่อเล่น", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveUserRole(role: String, displayName: String) {
        progressBar.visibility = View.VISIBLE

        val user = auth.currentUser
        if (user != null) {
            val isGoogleUser = user.photoUrl != null &&
                    user.providerData.any { it.providerId == "google.com" }

            val userData = mapOf(
                "email" to (user.email ?: ""),
                "username" to (user.displayName ?: ""),
                "role" to role,
                "avatarId" to 0,
                "displayName" to displayName,
                "profileImageUrl" to (user.photoUrl?.toString() ?: ""),
                "isGoogleUser" to isGoogleUser,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "isStore" to (role == "merchant")
            )

            db.collection("users").document(user.uid)
                .set(userData)
                .addOnSuccessListener {
                    if (role == "merchant") {
                        val storeData = mapOf(
                            "storeId" to user.uid,
                            "storeName" to displayName,
                            "storeCategory" to "",
                            "storeImage" to (user.photoUrl?.toString() ?: ""),
                            "isStore" to true,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("stores").document(user.uid)
                            .set(storeData)
                            .addOnSuccessListener {
                                progressBar.visibility = View.GONE
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "เกิดข้อผิดพลาดในการสร้างข้อมูลร้านค้า: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        progressBar.visibility = View.GONE
                        startActivity(Intent(this, CustomerMainActivity::class.java))
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "ไม่พบข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

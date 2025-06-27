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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val merchantCard = findViewById<CardView>(R.id.merchantCard)
        val customerCard = findViewById<CardView>(R.id.customerCard)
        progressBar = findViewById(R.id.progressBar)

        merchantCard.setOnClickListener {
            showStoreNameDialog()
        }

        customerCard.setOnClickListener {
            showNicknameDialog()
        }
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
            val userData = mapOf(
                "email" to (user.email ?: ""),
                "username" to (user.displayName ?: ""),
                "role" to role,
                "avatarId" to 0,
                "displayName" to displayName,
                "profileImageUrl" to (user.photoUrl?.toString() ?: ""),
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
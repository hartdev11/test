package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInButton: SignInButton
    private lateinit var forgotPasswordButton: TextView

    private val RC_SIGN_IN = 9001

    private val googleSignInLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    Log.w("LoginActivity", "Google sign in failed", e)
                    Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            } else {
                Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupButton = findViewById<Button>(R.id.signupButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        progressBar = findViewById(R.id.progressBar)
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกอีเมลและรหัสผ่านให้ครบ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        db.collection("users").document(userId).get()
                            .addOnSuccessListener { document ->
                                progressBar.visibility = View.GONE
                                if (document != null && document.exists()) {
                                    val role = document.getString("role") ?: "customer"
                                    if (role == "merchant") {
                                        startActivity(Intent(this, MainActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, CustomerMainActivity::class.java))
                                    }
                                    finish()
                                } else {
                                    Toast.makeText(this, "ไม่พบข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "โหลดข้อมูลผู้ใช้ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        progressBar.visibility = View.GONE
                        val exception = task.exception
                        if (exception is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                            Toast.makeText(this, "ไม่พบอีเมลนี้ในระบบ", Toast.LENGTH_LONG).show()
                        } else if (exception is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, "รหัสผ่านไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, exception?.localizedMessage ?: "เข้าสู่ระบบล้มเหลว", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }

        signupButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        googleSignInButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            signInWithGoogle()
        }

        forgotPasswordButton.setOnClickListener {
            // สร้าง Dialog สำหรับกรอกอีเมล
            val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
            val emailInput = dialogView.findViewById<EditText>(R.id.emailInput)

            MaterialAlertDialogBuilder(this)
                .setTitle("ลืมรหัสผ่าน")
                .setView(dialogView)
                .setPositiveButton("ส่งอีเมล") { dialog, _ ->
                    val email = emailInput.text.toString().trim()
                    if (email.isEmpty()) {
                        Toast.makeText(this, "กรุณากรอกอีเมล", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    progressBar.visibility = View.VISIBLE
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            progressBar.visibility = View.GONE
                            if (task.isSuccessful) {
                                Toast.makeText(this, "ส่งอีเมลรีเซ็ตรหัสผ่านเรียบร้อยแล้ว กรุณาตรวจสอบอีเมลของคุณ", Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this, "ไม่สามารถส่งอีเมลรีเซ็ตรหัสผ่านได้: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
                .setNegativeButton("ยกเลิก", null)
                .show()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    db.collection("users").document(user?.uid!!).get()
                        .addOnSuccessListener { document ->
                            if (!document.exists()) {
                                progressBar.visibility = View.GONE
                                startActivity(Intent(this, RoleSelectionActivity::class.java))
                                finish()
                            } else {
                                val role = document.getString("role") ?: "customer"
                                progressBar.visibility = View.GONE
                                if (role == "merchant") {
                                    startActivity(Intent(this, MainActivity::class.java))
                                } else {
                                    startActivity(Intent(this, CustomerMainActivity::class.java))
                                }
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("LoginActivity", "Error checking user in Firestore: ${e.message}")
                            Toast.makeText(this, "Error checking user: ${e.message}", Toast.LENGTH_LONG).show()
                            progressBar.visibility = View.GONE
                        }
                } else {
                    Log.e("LoginActivity", "Firebase authentication with Google failed", task.exception)
                    Toast.makeText(this, "Firebase authentication failed: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }
    }
}

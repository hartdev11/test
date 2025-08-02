package com.natthasethstudio.sethpos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.Status
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.pm.PackageManager
import androidx.activity.result.IntentSenderRequest
import android.widget.LinearLayout

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var db: FirebaseFirestore
    private lateinit var oneTapClient: SignInClient
    private lateinit var googleSignInButton: LinearLayout
    private lateinit var forgotPasswordButton: TextView
    private var showOneTapUI = true

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            try {
                val credential = Identity.getSignInClient(this).getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Google Sign-In failed: No ID token", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
        } else {
            Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Debug: Print SHA-1 fingerprint
        printSHA1Fingerprint()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupButton = findViewById<Button>(R.id.signupButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        progressBar = findViewById(R.id.progressBar)
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton)

        oneTapClient = Identity.getSignInClient(this)

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
                        val user = auth.currentUser
                        user?.let { user ->
                            val userId = user.uid
                            db.collection("users").document(userId).get()
                                .addOnSuccessListener { document ->
                                    progressBar.visibility = View.GONE
                                    if (document != null && document.exists()) {
                                        val role = document.getString("role") ?: "customer"
                                        startActivity(Intent(this, when (role) {
                                            "merchant" -> MainActivity::class.java
                                            else -> CustomerMainActivity::class.java
                                        }))
                                        finish()
                                    } else {
                                        Toast.makeText(this, "ไม่พบข้อมูลผู้ใช้", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "โหลดข้อมูลผู้ใช้ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        } ?: run {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "ไม่สามารถเข้าสู่ระบบได้: ไม่พบผู้ใช้", Toast.LENGTH_SHORT).show()
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
        val signInRequest = BeginSignInRequest.Builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(false)
            .build()
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    signInLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener(this) { e ->
                Toast.makeText(this, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { user ->
                        // Always update user profile info (including photoUrl) in Firestore
                        val userData = hashMapOf<String, Any>(
                            "email" to (user.email ?: ""),
                            "name" to (user.displayName ?: ""),
                            "photoUrl" to (user.photoUrl?.toString() ?: ""),
                            "provider" to (user.providerData.firstOrNull { it.providerId == "google.com" }?.providerId ?: ""),
                            "lastLogin" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("users").document(user.uid)
                            .set(userData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                // Continue with role check and navigation
                                db.collection("users").document(user.uid).get()
                                    .addOnSuccessListener { document ->
                                        val role = document.getString("role")
                                        if (!document.exists() || role.isNullOrEmpty()) {
                                            progressBar.visibility = View.GONE
                                            startActivity(Intent(this, RoleSelectionActivity::class.java))
                                            finish()
                                        } else if (role == "merchant") {
                                            startActivity(Intent(this, MainActivity::class.java))
                                            finish()
                                        } else {
                                            startActivity(Intent(this, CustomerMainActivity::class.java))
                                            finish()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("LoginActivity", "Error checking user in Firestore: ${e.message}")
                                        Toast.makeText(this, "Error checking user: ${e.message}", Toast.LENGTH_LONG).show()
                                        progressBar.visibility = View.GONE
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("LoginActivity", "Error updating user profile: ${e.message}")
                                Toast.makeText(this, "Error updating user profile: ${e.message}", Toast.LENGTH_LONG).show()
                                progressBar.visibility = View.GONE
                            }
                    }
                } else {
                    Log.e("LoginActivity", "Firebase authentication with Google failed", task.exception)
                    Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
            }
    }

    private fun printSHA1Fingerprint() {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            }
            
            signatures.forEach { signature ->
                val md = java.security.MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (b in digest) {
                    val hex = Integer.toHexString(0xFF and b.toInt())
                    if (hex.length == 1) {
                        hexString.append('0')
                    }
                    hexString.append(hex)
                }
                Log.d("LoginActivity", "SHA-1 Fingerprint: ${hexString.toString()}")
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error getting SHA-1 fingerprint", e)
        }
    }
}

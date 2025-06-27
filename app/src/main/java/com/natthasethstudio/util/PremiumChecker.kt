package com.natthasethstudio.sethpos.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object PremiumChecker {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun isPremiumUser(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            val document = db.collection("premium_users").document(userId).get().await()
            document.getBoolean("isPremium") ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isPremiumStore(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            val document = db.collection("premium_stores").document(userId).get().await()
            document.getBoolean("isPremium") ?: false
        } catch (e: Exception) {
            false
        }
    }
} 
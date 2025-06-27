package com.natthasethstudio.sethpos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class MenuRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCategories(onComplete: (Result<QuerySnapshot>) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("menu_items")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { result ->
                    onComplete(Result.success(result))
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } else {
            onComplete(Result.failure(Exception("User not logged in")))
        }
    }

    fun getMenuItems(filterCategory: String?, onComplete: (Result<QuerySnapshot>) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            var query = firestore.collection("menu_items").whereEqualTo("userId", userId)
            if (filterCategory != null) {
                query = query.whereEqualTo("category", filterCategory)
            }

            query.get()
                .addOnSuccessListener { result ->
                    onComplete(Result.success(result))
                }
                .addOnFailureListener { exception ->
                    onComplete(Result.failure(exception))
                }
        } else {
            onComplete(Result.failure(Exception("User not logged in")))
        }
    }

    // Methods for fetching data will be added here

} 
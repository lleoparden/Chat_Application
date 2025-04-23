package com.example.chat_application

import android.content.ContentValues
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object UserSettings {
    var theme: Int = R.style.defaultTheme
    lateinit var userId: String

    fun setUserOnline(userId: String, db: FirebaseFirestore) {
        db.collection("users").document(userId)
            .update("online", true)
            .addOnFailureListener { e ->
                Log.e("UserSettings", "Error updating online status", e)
            }
    }

    fun setUserOffline(userId: String) {
        // Create a new instance since we might not have access to the original
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .update("online", false)
            .addOnFailureListener { e ->
                Log.e("UserSettings", "Error updating offline status", e)
            }
    }
}

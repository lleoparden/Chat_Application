package com.example.chat_application

import android.content.Context
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import java.io.File

object globalFunctions {
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
        db.collection("users").document(userId)
            .update("lastSeen", System.currentTimeMillis().toInt())
            .addOnFailureListener { e ->
                Log.e("UserSettings", "Error updating online status", e)
            }
    }

    fun loadImageFromUrl(url: String, profileImageView: ImageView) {
        Glide.with(profileImageView.context)
            .load(url)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .diskCacheStrategy(DiskCacheStrategy.ALL))
            .into(profileImageView)
    }
}
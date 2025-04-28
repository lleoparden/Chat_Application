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


     const val IMGBB_API_URL = "https://api.imgbb.com/1/upload"
     const val IMGBB_API_KEY = "38328309adada9acb189c19a81befaa6"

    private const val TAG = "GlobalFunctions"
    private val db = FirebaseFirestore.getInstance()

    // Cache for storing recently fetched user data
    private val userCache = mutableMapOf<String, UserData>()
    private val groupCache = mutableMapOf<String, String>()

    // Overload getUserData to work with both context and direct ID
    fun getUserData(context: Context, userId: String): UserData? {
        Log.d(TAG, "Context-based getUserData called for userId: $userId")
        // For now, just delegate to the callback-based version but return null
        // You might want to implement local storage lookup here later

        // Return cached value if available
        if (userCache.containsKey(userId)) {
            Log.d(TAG, "Returning cached user data for $userId from context-based method")
            return userCache[userId]
        }

        // Since we can't do synchronous Firebase calls safely, return null
        // but kick off an async fetch to populate the cache for next time
        getUserData(userId) { /* Just populate cache */ }
        return null
    }

    fun getUserData(userId: String, callback: (UserData?) -> Unit) {
        // First check the cache
//        if (userCache.containsKey(userId)) {
//            Log.d(TAG, "Returning cached user data for $userId")
//            callback(userCache[userId])
//            return
//        }

        Log.d(TAG, "Fetching user data from Firebase for $userId")
        // If not in cache, fetch from Firebase
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        Log.d(TAG, "Document exists for $userId: ${document.data}")
                        val userData = UserData(
                            uid = userId,
                            displayName = document.getString("displayName") ?: "",
                            phoneNumber = document.getString("phoneNumber") ?: "",
                            password = document.getString("password") ?: "",
                            userDescription = document.getString("userDescription") ?: "",
                            userStatus = document.getString("userStatus") ?: "",
                            online = document.getBoolean("online") ?: false,
                            lastSeen = document.getLong("lastSeen").toString(),
                            profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                        )

                        // Store in cache
                        userCache[userId] = userData
                        Log.d(TAG, "Successfully parsed user data for $userId: profilePicUrl=${userData.profilePictureUrl}")

                        callback(userData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user data for $userId", e)
                        callback(null)
                    }
                } else {
                    Log.d(TAG, "No user found with ID: $userId")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user data for $userId", e)
                callback(null)
            }
    }

    fun getGroupPfp(groupid: String, callback: (String?) -> Unit) {

//        if (groupCache.containsKey(groupid)) {
//            callback(groupCache[groupid])
//            return
//        }


        db.collection("groups").document(groupid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        val url = document.getString("groupPictureUrl") ?: ""
                        callback(url)  // Pass the URL to the callback
                        groupCache[groupid] = url
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing group data for $groupid", e)
                        callback(null)
                    }
                } else {
                    Log.d(TAG, "No group found with ID: $groupid")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching group data for $groupid", e)
                callback(null)
            }
    }

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


    fun determineOtherParticipantId(chat: Chat): String {
        // Check if the chat object has participantIds
        if (chat.participantIds != null && chat.participantIds.isNotEmpty()) {
            // Return the first ID that is not the current user
            for (id in chat.participantIds) {
                if (id != UserSettings.userId) {
                    return id
                }
            }
        }
        return ""
    }
}
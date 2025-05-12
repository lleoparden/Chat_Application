package com.example.chat_application

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import java.io.File

object HelperFunctions {


    const val IMGBB_API_URL = "https://api.imgbb.com/1/upload"
    const val IMGBB_API_KEY = "38328309adada9acb189c19a81befaa6"

    private const val TAG = "HelperFunctions"

    @SuppressLint("StaticFieldLeak")
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
        if (userCache.containsKey(userId)) {
            Log.d(TAG, "Returning cached user data for $userId")
            callback(userCache[userId])
            return
        }

        Log.d(TAG, "Fetching user data from Firebase for $userId")
        // If not in cache, fetch from Firebase
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    try {
                        Log.d(TAG, "Document exists for $userId: ${document.data}")

                        // Handle lastSeen safely - get as Long if possible, otherwise use empty string
                        val lastSeen = try {
                            document.getLong("lastSeen")?.toString() ?: ""
                        } catch (e: Exception) {
                            Log.w(TAG, "lastSeen is not a number for $userId, using empty string")
                            ""
                        }

                        val userData = UserData(
                            uid = userId,
                            displayName = document.getString("displayName") ?: "",
                            phoneNumber = document.getString("phoneNumber") ?: "",
                            password = document.getString("password") ?: "",
                            userDescription = document.getString("userDescription") ?: "",
                            userStatus = document.getString("userStatus") ?: "",
                            online = when (val onlineValue = document.get("online")) {
                                is Boolean -> onlineValue
                                is String -> onlineValue.equals("true", ignoreCase = true)
                                else -> false
                            },
                            lastSeen = lastSeen,
                            profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                        )

                        // Store in cache
                        userCache[userId] = userData
                        Log.d(
                            TAG,
                            "Successfully parsed user data for $userId: profilePicUrl=${userData.profilePictureUrl}"
                        )

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

    fun getAllUserIds(callback: (List<String>?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val userIdsList = mutableListOf<String>()

                    for (document in querySnapshot.documents) {
                        // Add each user's ID to our list
                        document.id.let { userId ->
                            userIdsList.add(userId)
                        }
                    }

                    Log.d(TAG, "Successfully fetched ${userIdsList.size} user IDs")
                    callback(userIdsList)
                } else {
                    Log.d(TAG, "No users found in database")
                    callback(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user IDs", e)
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
            .update("lastSeen", System.currentTimeMillis())
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
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
            )
            .into(profileImageView)
    }

    fun determineOtherParticipantId(chat: Chat): String? {
        val currentUserId = UserSettings.userId
        val participantIds = chat.participantIds // HashMap<String, Boolean>

        // Ensure this is a two-person chat
        if (participantIds.size != 2) {
            Log.w(
                TAG,
                "Chat is not a two-person chat, contains ${participantIds.size} participants"
            )
            return null
        }

        // Get the other user's ID (the key that's not the current user's ID)
        return participantIds.keys.firstOrNull { it != currentUserId }
    }

    fun loadUserById(userId: String, context: Context): UserData? {
        val file = File(context.filesDir, "local_user.json")

        if (!file.exists()) {
            Log.e(
                TAG,
                "loadUserById: local_user.json not found in files directory: ${file.absolutePath}"
            )
            return null
        }

        Log.d(TAG, "loadUserById: Reading file from: ${file.absolutePath}")

        try {
            // Read from local_user.json in internal files directory
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)

            // Search for user with matching ID
            for (i in 0 until jsonArray.length()) {
                val userObject = jsonArray.getJSONObject(i)

                if (userObject.getString("uid") == userId) {
                    // User found, create and return UserData object
                    return UserData(
                        uid = userObject.getString("uid"),
                        displayName = userObject.getString("displayName"),
                        phoneNumber = userObject.getString("phoneNumber"),
                        password = userObject.optString("password", ""),
                        userDescription = userObject.optString("userDescription", ""),
                        userStatus = userObject.optString("userStatus", ""),
                        online = userObject.optBoolean("online", false),
                        lastSeen = userObject.optString("lastSeen", ""),
                        profilePictureUrl = userObject.optString("profilePictureUrl", "")
                    )
                }
            }

            // User not found
            Log.d(TAG, "loadUserById: User with ID $userId not found")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "loadUserById: Error reading or parsing user data", e)
            return null
        }
    }
}
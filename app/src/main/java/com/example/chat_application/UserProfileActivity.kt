package com.example.chat_application

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.UserSettings
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "UserProfileActivity"

class UserProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    private lateinit var profileImage: ImageView
    private lateinit var displayNameText: TextView
    private lateinit var displayNumber: TextView
    private lateinit var displayDescription: TextView
    private lateinit var displayStatus: TextView
    private lateinit var startnewchatButton: Button

    private var currentUserId = ""
    private var viewingUserId = ""
    private var name = ""
    private var isOwnProfile = false

    private lateinit var chatsReference: DatabaseReference
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chat: Chat
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.user_profile)

        if (firebaseEnabled) {
            db = FirebaseFirestore.getInstance()
            firebaseDatabase = FirebaseDatabase.getInstance()
            chatsReference = firebaseDatabase.getReference("chats")
        }
        currentUserId = UserSettings.userId
        viewingUserId = intent.getStringExtra("USER_ID") ?: currentUserId
        isOwnProfile = currentUserId == viewingUserId

        initializeViews()
        updateUIWithUserData()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = if (isOwnProfile) "My Profile" else "User Profile"

        toolbar.setNavigationOnClickListener {
            navigateBack()
        }
        startnewchatButton.setOnClickListener {
            createNewChat(currentUserId, viewingUserId, name)
        }
    }

    private fun createNewChat(
        currentUserId: String,
        profileUserId: String,
        profileUserName: String
    ) {
        // Read existing chats or create a new array if file doesn't exist
        val existingChats = readChatsFromFile()

        // Check if a chat with the same participants already exists
        val existingChat = findExistingChat(existingChats, currentUserId, profileUserId)

        if (existingChat != null) {
            // Chat with same participants exists, open it
            val chat = chatFromJson(existingChat)
            openExistingChat(chat)
        } else {
            // Create a new chat
            val chat_id = UUID.randomUUID().toString()

            val participantIdsArray = JSONArray().apply {
                put(currentUserId)
                put(profileUserId)
            }

            val chatObject = JSONObject().apply {
                put("id", chat_id)
                put("name", profileUserName)
                put("lastMessage", "")
                put("timestamp", System.currentTimeMillis())
                put("unreadCount", 0)
                put("participantIds", participantIdsArray)
                put("type", "direct")
            }



            chat = Chat(
                id = chat_id,
                name = profileUserName,
                lastMessage = "",
                timestamp = System.currentTimeMillis(),
                unreadCount = hashMapOf(
                    profileUserId to 0,
                    currentUserId to 0
                ),
                participantIds = hashMapOf(
                    profileUserId to true,
                    currentUserId to true
                ),
                type = "direct"
            )

            // Add to existing chats and save
            existingChats.put(chatObject)
            val jsonString = existingChats.toString()
            writeChatsToFile(jsonString)

            saveChatToFirebase(chat)
            openNewChat(chat)
        }
    }

    // Find a chat with the exact same participants
    private fun findExistingChat(
        chats: JSONArray,
        currentUserId: String,
        profileUserId: String
    ): JSONObject? {
        for (i in 0 until chats.length()) {
            try {
                val chatObject = chats.getJSONObject(i)

                // Only check direct chats
                if (chatObject.getString("type") == "direct") {
                    val participantIds = chatObject.getJSONArray("participantIds")

                    // Check if the chat has exactly 2 participants and they match our users
                    if (participantIds.length() == 2) {
                        val participantsMatch =
                            (participantIds.getString(0) == currentUserId && participantIds.getString(
                                1
                            ) == profileUserId) ||
                                    (participantIds.getString(0) == profileUserId && participantIds.getString(
                                        1
                                    ) == currentUserId)

                        if (participantsMatch) {
                            return chatObject
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking existing chat: ${e.message}")
            }
        }
        return null
    }

    // Convert JSONObject to Chat object
    private fun chatFromJson(jsonObject: JSONObject): Chat {
        val participantIdsArray = jsonObject.getJSONArray("participantIds")
        val participantsList = hashMapOf<String, Boolean>()

        for (i in 0 until participantIdsArray.length()) {
            participantsList[participantIdsArray.getString(i)] = true
        }

        // Parse unreadCount as Map<String, Int>
        val unreadCountMap = mutableMapOf<String, Int>()
        if (jsonObject.has("unreadCount")) {
            val unreadCountJson = jsonObject.getJSONObject("unreadCount")
            val keys = unreadCountJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                unreadCountMap[key] = unreadCountJson.getInt(key)
            }
        }

        return Chat(
            id = jsonObject.getString("id"),
            name = jsonObject.getString("name"),
            lastMessage = jsonObject.optString("lastMessage", ""),
            timestamp = jsonObject.optLong("timestamp", 0),
            unreadCount = unreadCountMap,
            participantIds = participantsList,
            type = jsonObject.optString("type", "direct")
        )
    }


    // Open existing chat
    private fun openExistingChat(chat: Chat) {
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            putExtra("CHAT_OBJECT", chat)
        }
        startActivity(intent)
        finish()
    }

    // Open new chat
    private fun openNewChat(chat: Chat) {
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            putExtra("CHAT_OBJECT", chat)
        }
        startActivity(intent)
        finish()
    }

    private fun readChatsFromFile(): JSONArray {
        try {
            val file = File(filesDir, "chats.json")
            if (file.exists()) {
                val jsonString = file.readText()
                return JSONArray(jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from file: ${e.message}")
        }
        return JSONArray() // Return empty array if file doesn't exist or there's an error
    }

    private fun writeChatsToFile(jsonString: String) {
        try {
            val file = File(filesDir, "chats.json")
            file.writeText(jsonString)
            Log.d(TAG, "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file: ${e.message}")
        }
    }

    private fun updateUIWithUserData() {
        val userdata = HelperFunctions.loadUserById(viewingUserId, this)
        if (userdata != null) {
            displayNameText.text = userdata.displayName
            name = userdata.displayName
            displayNumber.text = userdata.phoneNumber
            displayDescription.text = userdata.userDescription
            displayStatus.text = userdata.userStatus
            HelperFunctions.loadImageFromUrl(userdata.profilePictureUrl, profileImage)

            profileImage.setOnClickListener {
                val intent = Intent(this, ImageViewActivity::class.java)
                intent.putExtra("image_url", userdata.profilePictureUrl)
                startActivity(intent)
            }
        }
    }



    private fun initializeViews() {
        profileImage = findViewById(R.id.profileImageView)
        displayNameText = findViewById(R.id.displayNameText)
        displayNumber = findViewById(R.id.displayPhoneText)
        displayDescription = findViewById(R.id.userDescriptionEdit)
        displayStatus = findViewById(R.id.userStatusEdit)
        startnewchatButton = findViewById(R.id.startChatButton)
    }


    private fun saveChatToFirebase(chat: Chat) {
        if (!resources.getBoolean(R.bool.firebaseOn)) {
            return
        }

        try {
            try {
                chatsReference.child(chat.id).setValue(chat)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully saved chat ${chat.id} to Firebase")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save chat ${chat.id}: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving chat ${chat.id} to Firebase: ${e.message}")
                e.printStackTrace()
            }


        } catch (e: Exception) {
            Log.e(TAG, "Exception during saveChatToFirebase: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun navigateBack() {
        val sourceActivity = intent.getStringExtra("came_from")

        if (sourceActivity == "ChatRoom") {
            val chatObject = intent.getParcelableExtra<Chat>("CHAT_OBJECT")
            if (chatObject != null) {
                val intent = Intent(this, ChatRoomActivity::class.java).apply {
                    putExtra("CHAT_OBJECT", chatObject)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
            } else {
                // Fallback to main activity if chat object is missing
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
            }
        } else {
            // Default to AddNewChatActivity or whatever the previous screen should be
            startActivity(Intent(this, AddNewChatActivity::class.java))
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        finish()
    }
}
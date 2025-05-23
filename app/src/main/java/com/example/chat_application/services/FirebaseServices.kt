@file:Suppress("IMPLICIT_CAST_TO_ANY")

package com.example.chat_application.services

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.adapters.ChatAdapter
import com.example.chat_application.ChatManager
import com.example.chat_application.HelperFunctions
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlin.properties.Delegates


/**
 * Service for managing Firebase connections and operations.
 * Handles real-time chat synchronization, user data management, and authentication.
 */

@SuppressLint("StaticFieldLeak")
object FirebaseService {
    // Firebase components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference


    // Active listeners
    private var chatsListener: ChildEventListener? = null
    private var connectionListener: ValueEventListener? = null

    private lateinit var context: Context
    private lateinit var tag: String
    private var firebaseEnabled by Delegates.notNull<Boolean>()

    /**
     * Initialize Firebase services
     */

    fun initialize(context: Context, logTag: String, enabled: Boolean) {
        // Store context and tag
        this.context = context
        this.tag = logTag

        // Exit immediately if Firebase is disabled
        if (!enabled) {
            Log.d(tag, "Firebase is disabled, skipping initialization")
            return
        }
        firebaseEnabled = enabled

        try {
            // Initialize Firebase components
            firebaseDatabase = FirebaseDatabase.getInstance()
            firestore = FirebaseFirestore.getInstance()
            chatsReference = firebaseDatabase.getReference("chats")

            Log.d(tag, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Firebase: ${e.message}")
        }
    }

    fun checkConnectionAndLoadChatsFromFirebase(
        localChats: List<Chat>,
        chatAdapter: ChatAdapter,
        chatManager: ChatManager
    ) {
        try {
            // First, ensure Firebase is properly initialized
            if (!this::firebaseDatabase.isInitialized) {
                Log.e(tag, "Firebase database not initialized")
                return
            }

            // Add timeout to fallback to local data if Firebase doesn't respond
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                Log.d(tag, "Firebase connection timeout - using local data")
                // Ensure UI is updated with at least local data
                chatManager.clear()
                chatManager.pushAll(localChats)
                chatAdapter.updateData(chatManager.getAll())
            }

            // Set timeout for 3 seconds (reduced from 5 for faster experience)
            timeoutHandler.postDelayed(timeoutRunnable, 3000)

            // Check network connectivity first to avoid unnecessary Firebase calls
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

            val isOnline =
                networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!isOnline) {
                Log.d(tag, "Device is offline, not attempting Firebase connection")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                return
            }

            // Use ValueEventListener instead of SingleValueEvent to catch connection changes
            val connectedRef = firebaseDatabase.getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            // Cancel timeout since we're connected
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            Log.d(tag, "Firebase connected, loading chats")
                            loadChatsFromFirebaseAndMerge(localChats, chatManager, chatAdapter)
                        } else {
                            // Still waiting for connection or disconnected
                            Log.d(tag, "Firebase not connected yet")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing connection status: ${e.message}")
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        // Ensure we have local data
                        chatManager.clear()
                        chatManager.pushAll(localChats)
                        chatAdapter.updateData(chatManager.getAll())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Cancel timeout in case of explicit cancellation
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    Log.e(tag, "Failed to check connection status: ${error.message}")
                    // Ensure we have local data
                    chatManager.clear()
                    chatManager.pushAll(localChats)
                    chatAdapter.updateData(chatManager.getAll())
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Exception checking Firebase connection: ${e.message}")
            // Ensure we have local data as a fallback
            chatManager.clear()
            chatManager.pushAll(localChats)
            chatAdapter.updateData(chatManager.getAll())
        }
    }

    // Improved fetchUserDataAndUpdateDisplayNames to handle offline case properly
    fun fetchUserDataAndUpdateDisplayNames(chatManager: ChatManager, chatAdapter: ChatAdapter) {
        // Check if Firestore is initialized
        if (!this::firestore.isInitialized) {
            Log.e(tag, "Firestore not initialized")
            return
        }

        // Create a map to store user IDs and their display names
        val userDisplayNames = mutableMapOf<String, String>()

        // Get all chats to find all participant IDs
        val allChats = chatManager.getAll()
        val allParticipantIds = HashMap<String, Boolean>()

        // Collect all unique participant IDs
        for (chat in allChats) {
            chat.participantIds.forEach { (userId, active) ->
                if (active) {
                    allParticipantIds[userId] = true
                }
            }
        }

        // Remove the current user ID
        allParticipantIds.remove(UserSettings.userId)

        // If there are no other participants, we're done
        if (allParticipantIds.isEmpty()) {
            return
        }

        // Add timeout to prevent blocking if Firestore is slow/offline
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.d(tag, "Firestore user fetch timeout - using IDs as display names")
            // Use IDs as display names as fallback
            for (userId in allParticipantIds.keys) {
                userDisplayNames[userId] = userId
            }
            updateDisplayNamesAndRefresh(userDisplayNames, chatManager, chatAdapter)
        }

        // Set timeout for 3 seconds
        timeoutHandler.postDelayed(timeoutRunnable, 3000)

        // Keep track of how many users we've processed
        var processedCount = 0

        // Query Firestore for each user's data
        for (userId in allParticipantIds.keys) {
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Get the user's display name from Firestore
                        val displayName = document.getString("displayName") ?: userId

                        // Add to our map
                        userDisplayNames[userId] = displayName

                        Log.d(tag, "Fetched user $userId with display name: $displayName")
                    } else {
                        // If user document doesn't exist, use the ID as display name
                        userDisplayNames[userId] = userId
                        Log.d(tag, "User document not found for $userId")
                    }

                    // Increment processed count
                    processedCount++

                    // If we've processed all users, update display names
                    if (processedCount == allParticipantIds.size) {
                        // Cancel timeout as we've completed normally
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        updateDisplayNamesAndRefresh(userDisplayNames, chatManager, chatAdapter)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "Error fetching user $userId: ${e.message}")

                    // Use the ID as display name in case of failure
                    userDisplayNames[userId] = userId

                    // Increment processed count
                    processedCount++

                    // If we've processed all users, proceed even with some errors
                    if (processedCount == allParticipantIds.size) {
                        // Cancel timeout as we've completed normally
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        updateDisplayNamesAndRefresh(userDisplayNames, chatManager, chatAdapter)
                    }
                }
        }
    }

    /**
     * Set up real-time listeners for chat updates
     */
    private fun setupRealTimeChatListener(chatManager: ChatManager, chatAdapter: ChatAdapter) {
        // Remove existing listener if any
        removeRealTimeChatListener()

        // Create and attach new listener
        chatsListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val chat = extractChatFromSnapshot(snapshot)
                    // Only add chats where the current user is an ACTIVE participant (value is true)
                    if (chat != null &&
                        chat.participantIds.containsKey(UserSettings.userId) &&
                        chat.participantIds[UserSettings.userId] == true
                    ) {

                        // Check if this chat already exists in the manager
                        val existingChat = chatManager.getChatById(chat.id)

                        if (existingChat == null) {
                            // Chat doesn't exist - add it
                            chatManager.push(chat)
                            chatAdapter.updateData(chatManager.getAll())
                            LocalStorageService.saveChatsToLocalStorage(chatManager)
                            Log.d(
                                tag,
                                "New chat added from realtime event: ${chat.getEffectiveDisplayName()}"
                            )
                        } else {
                            // Chat exists - update if needed based on timestamp
                            if (existingChat.timestamp < chat.timestamp) {
                                chatManager.updateById(chat.id, chat)
                                chatAdapter.updateData(chatManager.getAll())
                                LocalStorageService.saveChatsToLocalStorage(chatManager)
                                Log.d(
                                    tag,
                                    "Existing chat updated with newer data: ${chat.getEffectiveDisplayName()}"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing added chat: ${e.message}")
                }
            }


            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val chat = extractChatFromSnapshot(snapshot)
                    // Apply the same condition here - only update if the user is an ACTIVE participant
                    if (chat != null &&
                        chat.participantIds.containsKey(UserSettings.userId) &&
                        chat.participantIds[UserSettings.userId] == true
                    ) {

                        // Update existing chat
                        chatManager.updateById(chat.id, chat)
                        chatAdapter.updateData(chatManager.getAll())
                        LocalStorageService.saveChatsToLocalStorage(chatManager)
                        Log.d(tag, "Chat updated: ${chat.getEffectiveDisplayName()}")
                    } else if (chat != null &&
                        chat.participantIds.containsKey(UserSettings.userId) &&
                        chat.participantIds[UserSettings.userId] == false
                    ) {
                        // User was removed from this chat, so remove it from the UI
                        chatManager.removeById(chat.id)
                        chatAdapter.updateData(chatManager.getAll())
                        LocalStorageService.saveChatsToLocalStorage(chatManager)
                        Log.d(tag, "Chat removed (user inactive): ${chat.id}")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing changed chat: ${e.message}")
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                try {
                    val chatId = snapshot.key
                    if (chatId != null) {
                        // Remove from chat manager
                        chatManager.removeById(chatId)
                        chatAdapter.updateData(chatManager.getAll())
                        LocalStorageService.saveChatsToLocalStorage(chatManager)
                        Log.d(tag, "Chat removed: $chatId")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing removed chat: ${e.message}")
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not handling chat reordering
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Chat listener cancelled: ${error.message}")
            }
        }

        // Attach the listener
        chatsReference.addChildEventListener(chatsListener!!)
        Log.d(tag, "Real-time chat listener established")
    }

    /**
     * Extract Chat object from Firebase DataSnapshot
     */
    private fun extractChatFromSnapshot(chatSnapshot: DataSnapshot): Chat? {
        try {
            val chatId = chatSnapshot.key ?: return null
            val name = chatSnapshot.child("name").getValue(String::class.java) ?: ""
            val displayName = chatSnapshot.child("displayName").getValue(String::class.java) ?: ""
            val lastMessage = chatSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
            val timestamp = chatSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            val unreadCountSnapshot = chatSnapshot.child("unreadCount")
            val unreadCount = mutableMapOf<String, Int>()
            for (entry in unreadCountSnapshot.children) {
                val userId = entry.key
                val count = entry.getValue(Int::class.java) ?: 0
                if (userId != null) {
                    unreadCount[userId] = count
                }
            }

            val type = chatSnapshot.child("type").getValue(String::class.java) ?: "direct"

            // Get participant IDs
            val participantIds = HashMap<String, Boolean>()
            val participantsSnapshot = chatSnapshot.child("participantIds")
            for (participantSnapshot in participantsSnapshot.children) {
                val participantId = participantSnapshot.key
                val participantValue = participantSnapshot.getValue(Boolean::class.java) ?: true
                if (participantId != null) {
                    participantIds[participantId] = participantValue
                }
            }

            // Create and return chat object
            return Chat(
                id = chatId,
                name = name,
                displayName = displayName,
                lastMessage = lastMessage,
                timestamp = timestamp,
                unreadCount = unreadCount,
                participantIds = participantIds,
                type = type
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting chat from snapshot: ${e.message}")
            return null
        }
    }

    /**
     * Remove real-time chat listener to prevent memory leaks
     */
    private fun removeRealTimeChatListener() {
        chatsListener?.let {
            chatsReference.removeEventListener(it)
            chatsListener = null
            Log.d(TAG, "Real-time chat listener removed")
        }
    }

    /**
     * Remove connection status listener to prevent memory leaks
     */
    private fun removeConnectionListener() {
        connectionListener?.let {
            firebaseDatabase.getReference(".info/connected").removeEventListener(it)
            connectionListener = null
            Log.d(TAG, "Connection listener removed")
        }
    }

    /**
     * Clean up all Firebase listeners
     */
    fun cleanup() {
        removeRealTimeChatListener()
        removeConnectionListener()
    }

    /**
     * Delete a chat by ID from Firebase
     */
    fun removeUserFromParticipants(chatId: String) {
        val currentUserId = UserSettings.userId

        chatsReference.child(chatId)
            .child("participantIds")
            .child(currentUserId)
            .setValue(false)

    }


    /**
     * Update display names in chat manager and refresh UI
     */
    private fun updateDisplayNamesAndRefresh(
        userDisplayNames: Map<String, String>,
        chatManager: ChatManager,
        chatAdapter: ChatAdapter
    ) {
        // Update display names in chat manager
        chatManager.updateDisplayNames(userDisplayNames)

        // Update UI
        chatAdapter.updateData(chatManager.getAll())

        // Save changes
        LocalStorageService.saveChatsToLocalStorage(chatManager)

        // Update Firebase
        saveChatsToFirebase(chatManager)
    }


    /**
     * Load chats from Firebase and merge with local chats
     */
    private fun loadChatsFromFirebaseAndMerge(
        localChats: List<Chat>,
        chatManager: ChatManager,
        chatAdapter: ChatAdapter
    ) {
        Log.d(tag, "Loading chats from Firebase")

        try {
            chatsReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val firebaseChats = mutableListOf<Chat>()
                        val mergedChats = mutableMapOf<String, Chat>() // Using ID as key

                        // First add all local chats to the merged map
                        for (chat in localChats) {
                            mergedChats[chat.id] = chat
                        }

                        // Process Firebase chats
                        for (chatSnapshot in snapshot.children) {
                            try {
                                val chat = extractChatFromSnapshot(chatSnapshot)

                                // Only process chats where the current user is an ACTIVE participant
                                if (chat != null &&
                                    chat.participantIds.containsKey(UserSettings.userId) &&
                                    chat.participantIds[UserSettings.userId] == true
                                ) {

                                    firebaseChats.add(chat)
                                    // Firebase data overrides local data for the same chat ID
                                    mergedChats[chat.id] = chat
                                    Log.d(
                                        tag,
                                        "Added Firebase chat: ${chat.getEffectiveDisplayName()} (ID: ${chat.id})"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing chat from Firebase: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        Log.d(tag, "Loaded ${firebaseChats.size} chats from Firebase")

                        try {
                            // Update the chat manager with merged chats
                            chatManager.clear()
                            chatManager.pushAll(mergedChats.values.toList())

                            // Update adapter with the merged data
                            chatAdapter.updateData(chatManager.getAll())

                            // Save the merged data back to local storage
                            LocalStorageService.saveChatsToLocalStorage(chatManager)

                            Log.d(tag, "Merged ${mergedChats.size} chats from local and Firebase")

                            // Set up real-time listener for future updates
                            setupRealTimeChatListener(chatManager, chatAdapter)
                        } catch (e: Exception) {
                            Log.e(tag, "Error updating UI with merged chats: ${e.message}")
                            e.printStackTrace()

                            // Fallback to local chats
                            chatManager.clear()
                            chatManager.pushAll(localChats)
                            chatAdapter.updateData(chatManager.getAll())
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error in onDataChange: ${e.message}")
                        e.printStackTrace()

                        // Fallback to local chats
                        chatManager.clear()
                        chatManager.pushAll(localChats)
                        chatAdapter.updateData(chatManager.getAll())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Failed to load chats from Firebase: ${error.message}")

                    // Just use the local chats if Firebase failed
                    chatManager.clear()
                    chatManager.pushAll(localChats)
                    chatAdapter.updateData(chatManager.getAll())
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Exception during Firebase chat loading: ${e.message}")
            e.printStackTrace()

            // Fallback to local chats
            chatManager.clear()
            chatManager.pushAll(localChats)
            chatAdapter.updateData(chatManager.getAll())
        }
    }

    /**
     * Save chats to Firebase database
     */
    fun saveChatsToFirebase(chatManager: ChatManager) {
        if (!this::chatsReference.isInitialized) {
            return
        }

        try {
            Log.d(tag, "Saving chats to Firebase")
            val allChats = chatManager.getAll()

            // Only update the chats, don't delete anything
            for (chat in allChats) {
                try {
                    // Only save if user is an ACTIVE participant
                    if (chat.participantIds.containsKey(UserSettings.userId) &&
                        chat.participantIds[UserSettings.userId] == true
                    ) {

                        chatsReference.child(chat.id).setValue(chat)
                            .addOnSuccessListener {
                                Log.d(tag, "Successfully saved chat ${chat.id} to Firebase")
                            }
                            .addOnFailureListener { e ->
                                Log.e(tag, "Failed to save chat ${chat.id}: ${e.message}")
                            }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error saving chat ${chat.id} to Firebase: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.d(tag, "Scheduled ${allChats.size} chats to be saved to Firebase")
        } catch (e: Exception) {
            Log.e(tag, "Exception during saveChatsToFirebase: ${e.message}")
            e.printStackTrace()
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Verify user exists in Firestore
     */
    fun verifyUserInFirestore(
        userId: String,
        onSuccess: (UserData) -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // User exists in Firestore
                    val userData = document.data
                    val userToReturn = UserData(
                        uid = userId,
                        displayName = userData?.get("displayName") as? String ?: "",
                        phoneNumber = userData?.get("phoneNumber") as? String ?: "",
                        password = userData?.get("password") as? String ?: "",
                        userDescription = userData?.get("userDescription") as? String ?: "",
                        userStatus = userData?.get("userStatus") as? String ?: "",
                        online = userData?.get("online") as? Boolean ?: false,
                        lastSeen = userData?.get("lastSeen") as? String ?: "",
                        profilePictureUrl = userData?.get("profilePictureUrl") as? String ?: ""
                    )
                    onSuccess(userToReturn)
                } else {
                    // User ID not found in Firestore
                    onFailure(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking user login status", e)
                onFailure(e)
            }
    }

    /**
     * Check if a user exists in the remote database
     */
    fun checkUserExistsRemote(
        phoneNumber: String,
        onExists: () -> Unit,
        onNotExists: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    onExists()
                } else {
                    onNotExists()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking if user exists", e)
                onError(e)
            }
    }

    /**
     * Generate a new user ID
     */
    fun generateUserId(): String {
        return firestore.collection("users").document().id
    }

    /**
     * Save user data to Firebase
     */
    fun saveUserToFirebase(
        userData: HashMap<String, String>,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    /**
     * Create user data hashmap for Firebase
     */
    fun createUserData(
        userId: String,
        name: String,
        phone: String,
        password: String
    ): HashMap<String, String> {
        return hashMapOf(
            "uid" to userId,
            "displayName" to name,
            "phoneNumber" to phone,
            "password" to password,
            "userDescription" to "",
            "userStatus" to "",
            "online" to "false",
            "lastSeen" to "",
            "profilePictureUrl" to ""
        )
    }

    /**
     * Validate login credentials against remote database
     */
    fun validateLoginRemote(
        phoneNumber: String,
        password: String,
        onSuccess: (String, String) -> Unit,
        onIncorrectPassword: () -> Unit,
        onUserNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                handleLoginQueryResult(
                    documents,
                    password,
                    onSuccess,
                    onIncorrectPassword,
                    onUserNotFound
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error validating login", e)
                onError(e)
            }
    }

    /**
     * Handle login query result from Firestore
     */
    private fun handleLoginQueryResult(
        documents: QuerySnapshot,
        password: String,
        onSuccess: (String, String) -> Unit,
        onIncorrectPassword: () -> Unit,
        onUserNotFound: () -> Unit
    ) {
        if (!documents.isEmpty) {
            // User found, check password
            var isPasswordCorrect = false
            var userId = ""
            var displayName = ""

            for (document in documents) {
                val userPassword = document.getString("password")
                if (userPassword == password) {
                    isPasswordCorrect = true
                    userId = document.getString("uid") ?: document.id
                    displayName = document.getString("displayName") ?: ""
                    break
                }
            }

            if (isPasswordCorrect) {
                onSuccess(userId, displayName)
            } else {
                onIncorrectPassword()
            }
        } else {
            onUserNotFound()
        }
    }

    fun loadUserFromFirebase(
        userId: String,
        callback: (UserData) -> Unit
    ) {
        Log.d(tag, "Starting to load user data for userId: $userId")

        if (!firebaseEnabled) {
            Log.d(tag, "Firebase disabled, using local storage for user: $userId")
            LocalStorageService.loadUserFromLocalStorage(userId) { user ->
                Log.d(tag, "User data loaded from local storage: ${user.displayName}")
                callback(user)
            }
            return // prevent continuing to Firebase
        }

        Log.d(tag, "Fetching user data from Firebase for userId: $userId")
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d(tag, "Firebase document found for user: $userId")

                    val lastSeen = try {
                        document.getLong("lastSeen")?.toString() ?: ""
                    } catch (e: Exception) {
                        Log.w(tag, "lastSeen is not a number for $userId, using empty string")
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
                    Log.d(
                        tag,
                        "User data parsed successfully: ${userData.displayName}, online: ${userData.online}"
                    )
                    callback(userData)
                } else {
                    Log.d(
                        tag,
                        "User not found in Firebase for userId: $userId, trying local storage"
                    )
                    LocalStorageService.loadUserFromLocalStorage(userId) { user ->
                        Log.d(
                            tag,
                            "Fallback: User data loaded from local storage: ${user.displayName}"
                        )
                        callback(user)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error loading user data from Firebase for userId: $userId", e)
                Log.e(tag, "Error details: ${e.message}")
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                Log.d(tag, "Attempting local storage fallback after Firebase failure")
                LocalStorageService.loadUserFromLocalStorage(userId) { user ->
                    Log.d(
                        tag,
                        "Error fallback: User data loaded from local storage: ${user.displayName}"
                    )
                    callback(user)
                }
            }
    }


    fun updateUserinFirebase(
        userId: String,
        data: HashMap<String, Any>,
        callback: (Boolean) -> Unit
    ) {
        if (!firebaseEnabled) {
            callback(false)
            return
        }

        firestore.collection("users").document(userId)
            .update(data)
            .addOnSuccessListener {
                // Update in chat list
                Log.d(tag, "uploaded successfully")
                callback(true)

            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error updating profile in Firebase", e)
                callback(false)
            }
    }

}
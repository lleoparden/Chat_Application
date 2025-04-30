package com.example.chat_application.services

import android.content.ContentValues.TAG
import android.util.Log
import com.example.chat_application.Chat
import com.example.chat_application.ChatAdapter
import com.example.chat_application.ChatManager
import com.example.chat_application.UserData
import com.example.chat_application.UserSettings
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot


/**
 * Service for managing Firebase connections and operations.
 * Handles real-time chat synchronization, user data management, and authentication.
 */
object FirebaseService {
    // Firebase components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference
    private lateinit var usersReference: DatabaseReference

    // Active listeners
    private var chatsListener: ChildEventListener? = null
    private var connectionListener: ValueEventListener? = null

    /**
     * Initialize Firebase services
     */
    fun initialize() {
        firestore = FirebaseFirestore.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()
        chatsReference = firebaseDatabase.getReference("chats")
        usersReference = firebaseDatabase.getReference("users")
    }

    /**
     * Set up Firebase connection monitoring and fallback mechanisms
     */
    fun setupFirebase(tag: String, chatManager: ChatManager, chatAdapter: ChatAdapter) {
        try {
            // Add connection status listener with improved error handling
            val connectedRef = firebaseDatabase.getReference(".info/connected")
            connectionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (!connected) {
                        Log.w(tag, "Device is offline, using local data")
                        LocalStorageService.loadChatsFromLocalStorageAndDisplay(chatManager, chatAdapter, tag)
                    } else {
                        // If we're connected, set up real-time chat listener
                        setupRealTimeChatListener(tag, chatManager, chatAdapter)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Firebase connection listener cancelled: ${error.message}")
                    // When connection is cancelled, fall back to local data
                    LocalStorageService.loadChatsFromLocalStorageAndDisplay(chatManager, chatAdapter, tag)
                }
            }

            connectedRef.addValueEventListener(connectionListener!!)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Firebase: ${e.message}")
            e.printStackTrace()

            // Fall back to local storage
            LocalStorageService.loadChatsFromLocalStorageAndDisplay(chatManager, chatAdapter, tag)
        }
    }

    /**
     * Set up real-time listeners for chat updates
     */
    private fun setupRealTimeChatListener(tag: String, chatManager: ChatManager, chatAdapter: ChatAdapter) {
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
                        chat.participantIds[UserSettings.userId] == true) {

                        // Check if this chat already exists in the manager
                        val existingChat = chatManager.getChatById(chat.id)

                        if (existingChat == null) {
                            // Chat doesn't exist - add it
                            chatManager.push(chat)
                            chatAdapter.updateData(chatManager.getAll())
                            LocalStorageService.saveChatsToLocalStorage(chatManager, tag)
                            Log.d(tag, "New chat added from realtime event: ${chat.getEffectiveDisplayName()}")
                        } else {
                            // Chat exists - update if needed based on timestamp
                            if (existingChat.timestamp < chat.timestamp) {
                                chatManager.updateById(chat.id, chat)
                                chatAdapter.updateData(chatManager.getAll())
                                LocalStorageService.saveChatsToLocalStorage(chatManager, tag)
                                Log.d(tag, "Existing chat updated with newer data: ${chat.getEffectiveDisplayName()}")
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
                        chat.participantIds[UserSettings.userId] == true) {

                        // Update existing chat
                        chatManager.updateById(chat.id, chat)
                        chatAdapter.updateData(chatManager.getAll())
                        LocalStorageService.saveChatsToLocalStorage(chatManager, tag)
                        Log.d(tag, "Chat updated: ${chat.getEffectiveDisplayName()}")
                    } else if (chat != null &&
                        chat.participantIds.containsKey(UserSettings.userId) &&
                        chat.participantIds[UserSettings.userId] == false) {
                        // User was removed from this chat, so remove it from the UI
                        chatManager.removeById(chat.id)
                        chatAdapter.updateData(chatManager.getAll())
                        LocalStorageService.saveChatsToLocalStorage(chatManager, tag)
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
                        LocalStorageService.saveChatsToLocalStorage(chatManager, tag)
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
            val unreadCount = chatSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0
            val type = chatSnapshot.child("type").getValue(String::class.java) ?: "direct"

            // Get participant IDs
            val participantIds = HashMap<String,Boolean>()
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
    fun removeRealTimeChatListener() {
        chatsListener?.let {
            chatsReference.removeEventListener(it)
            chatsListener = null
            Log.d(TAG, "Real-time chat listener removed")
        }
    }

    /**
     * Remove connection status listener to prevent memory leaks
     */
    fun removeConnectionListener() {
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
     * Fetch user data and update display names in the UI
     */
    fun fetchUserDataAndUpdateDisplayNames(chatManager: ChatManager, tag: String, chatAdapter: ChatAdapter) {
        // Create a map to store user IDs and their display names
        val userDisplayNames = mutableMapOf<String, String>()

        // Get all chats to find all participant IDs
        val allChats = chatManager.getAll()
        val allParticipantIds = HashMap<String,Boolean>()

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
                        updateDisplayNamesAndRefresh(userDisplayNames, chatManager, chatAdapter, tag)
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
                        updateDisplayNamesAndRefresh(userDisplayNames, chatManager, chatAdapter, tag)
                    }
                }
        }
    }

    /**
     * Update display names in chat manager and refresh UI
     */
    private fun updateDisplayNamesAndRefresh(userDisplayNames: Map<String, String>, chatManager: ChatManager, chatAdapter: ChatAdapter, tag: String) {
        // Update display names in chat manager
        chatManager.updateDisplayNames(userDisplayNames)

        // Update UI
        chatAdapter.updateData(chatManager.getAll())

        // Save changes
        LocalStorageService.saveChatsToLocalStorage(chatManager, tag)

        // Update Firebase
        saveChatsToFirebase(chatManager, tag)
    }

    /**
     * Check connection status and load chats from Firebase if online
     */
    fun checkConnectionAndLoadChatsFromFirebase(localChats: List<Chat>, tag: String, chatAdapter: ChatAdapter, chatManager: ChatManager) {
        try {
            val connectedRef = firebaseDatabase.getReference(".info/connected")
            connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            loadChatsFromFirebaseAndMerge(localChats, tag, chatManager, chatAdapter)
                        } else {
                            // Explicitly handle the offline case
                            Log.d(tag, "Device is offline, using local data only")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing connection status: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(tag, "Failed to check connection status: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Exception checking Firebase connection: ${e.message}")
        }
    }

    /**
     * Load chats from Firebase and merge with local chats
     */
    private fun loadChatsFromFirebaseAndMerge(localChats: List<Chat>, tag: String, chatManager: ChatManager, chatAdapter: ChatAdapter) {
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
                                    chat.participantIds[UserSettings.userId] == true) {

                                    firebaseChats.add(chat)
                                    // Firebase data overrides local data for the same chat ID
                                    mergedChats[chat.id] = chat
                                    Log.d(tag, "Added Firebase chat: ${chat.getEffectiveDisplayName()} (ID: ${chat.id})")
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
                            LocalStorageService.saveChatsToLocalStorage(chatManager, tag)

                            Log.d(tag, "Merged ${mergedChats.size} chats from local and Firebase")

                            // Set up real-time listener for future updates
                            setupRealTimeChatListener(tag, chatManager, chatAdapter)
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
    fun saveChatsToFirebase(chatManager: ChatManager, tag: String) {
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
                        chat.participantIds[UserSettings.userId] == true) {

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
    fun createUserData(userId: String, name: String, phone: String, password: String): HashMap<String, String> {
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
                handleLoginQueryResult(documents, password, onSuccess, onIncorrectPassword, onUserNotFound)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error validating login", e)
                onError(e)
            }
    }

    /**
     * Handle login query result from Firestore
     */
    fun handleLoginQueryResult(
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
}
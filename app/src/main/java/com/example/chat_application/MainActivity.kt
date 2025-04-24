package com.example.chat_application

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.UserSettings.userId
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "MainActivity"
private const val CHATS_FILE = "chats.json"
private const val USERS_FILE = "users.json"

class MainActivity : AppCompatActivity(), ChatAdapter.OnChatClickListener {

    // UI Components
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var searchButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newChatFab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var searchBar: EditText
    private lateinit var searchContainer: LinearLayout
    private var isSearchVisible = false

    // Data Components
    private lateinit var chatAdapter: ChatAdapter
    private val chatManager = ChatManager()
    private val userDisplayNames = mutableMapOf<String, String>()

    // Firebase Components
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference
    private lateinit var usersReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainpage)

        initViews()
        setupUI()
        setupRecyclerView()
        setupFirebase()

        // Load data
        loadUsers()
    }

    //region UI Setup
    private fun initViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        searchButton = findViewById(R.id.searchButton)
        settingsButton = findViewById(R.id.settingsButton)
        newChatFab = findViewById(R.id.newChatFab)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        searchBar = findViewById(R.id.searchBar)
        searchContainer = findViewById(R.id.searchContainer)

        // Initially hide search bar
        searchContainer.visibility = View.GONE
    }

    private fun setupUI() {
        // Setup search button
        searchButton.setOnClickListener {
            toggleSearchBar()
        }

        // Setup search bar functionality
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        // Setup settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // Setup FAB
        newChatFab.setOnClickListener {
            startActivity(Intent(this, AddNewChatActivity::class.java))
            finish()
        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chats -> true // Already on chats page
                R.id.navigation_stories -> {
                    startActivity(Intent(this, StoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatManager, this)
        chatRecyclerView.adapter = chatAdapter
    }

    private fun toggleSearchBar() {
        isSearchVisible = !isSearchVisible

        if (isSearchVisible) {
            searchContainer.visibility = View.VISIBLE
            searchBar.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)
        } else {
            searchContainer.visibility = View.GONE
            searchBar.text.clear()
            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchBar.windowToken, 0)

            // Reset RecyclerView to show all chats
            chatAdapter = ChatAdapter(chatManager, this)
            chatRecyclerView.adapter = chatAdapter
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            // Show all chats when query is empty
            chatAdapter = ChatAdapter(chatManager, this)
            chatRecyclerView.adapter = chatAdapter
            return
        }

        // Use binary search to find matching chats
        val matchingChat = chatManager.findByName(query)

        if (matchingChat != null) {
            // Show only the exact match
            val singleChatManager = ChatManager()
            singleChatManager.push(matchingChat)
            chatAdapter = ChatAdapter(singleChatManager, this)
            chatRecyclerView.adapter = chatAdapter
        } else {
            // If no exact match, try to find partial matches
            val partialMatches = chatManager.findPartialMatches(query)

            if (partialMatches.isNotEmpty()) {
                val tempChatManager = ChatManager()
                tempChatManager.pushAll(partialMatches)
                chatAdapter = ChatAdapter(tempChatManager, this)
                chatRecyclerView.adapter = chatAdapter
            } else {
                // Display a message for no results
                Toast.makeText(this, "No chats found matching '$query'", Toast.LENGTH_SHORT).show()
                // Show empty list
                val emptyChatManager = ChatManager()
                chatAdapter = ChatAdapter(emptyChatManager, this)
                chatRecyclerView.adapter = chatAdapter
            }
        }
    }
    //endregion

    //region Chat Management
    private fun loadUsers() {
        Log.d(TAG, "Loading users")

        // First load from local storage
        loadUsersFromLocalStorage()

        // If no users found locally, add demo users
        if (userDisplayNames.isEmpty()) {
            addDemoUsers()
        }

        // If Firebase is enabled and we're online, load from there
        if (resources.getBoolean(R.bool.firebaseOn)) {
            checkConnectionAndLoadUsersFromFirebase()
        } else {
            // If Firebase is disabled, proceed directly to loading chats
            loadChats()
        }
    }

    private fun loadChats() {
        Log.d(TAG, "Loading chats")

        // First load from local storage for immediate display
        val localChats = loadChatsFromLocalStorageWithoutSaving()

        // If no local chats, add demo chats for first-time users
        if (localChats.isEmpty()) {
            Log.d(TAG, "No local chats found, adding demo chats")
            addDemoChat()
            return // The addDemoChat function will handle everything else
        }

        // Update UI with local chats immediately
        chatManager.clear()
        chatManager.pushAll(localChats)
        updateChatDisplayNames() // Update display names
        chatAdapter.notifyDataSetChanged()

        // Only attempt Firebase loading if explicitly enabled AND we're online
        if (resources.getBoolean(R.bool.firebaseOn)) {
            checkConnectionAndLoadChatsFromFirebase(localChats)
        }
    }

    private fun addDemoUsers() {
        Log.d(TAG, "Adding demo users")

        userDisplayNames["demo_user_1"] = "John Doe"
        userDisplayNames["demo_user_2"] = "Jane Smith"

        // Save demo users to local storage
        saveUsersToLocalStorage()
    }

    private fun addDemoChat() {
        Log.d(TAG, "Adding demo chats")
        try {
            chatManager.clear()
            val currentTime = System.currentTimeMillis()

            // Make sure userId is included in the participant IDs
            val demoChats = listOf(
                Chat(
                    id = "demo1",
                    name = "Demo Group",
                    displayName = "Demo Group", // Same for group chats
                    lastMessage = "Welcome to FireChat! This is a demo message.",
                    timestamp = currentTime - 6000,
                    unreadCount = 9,
                    participantIds = mutableListOf(userId, "demo_user_1", "demo_user_2"),
                    type = "group"
                ),
                Chat(
                    id = "demo2",
                    name = "John Doe", // Original name
                    displayName = "John Doe", // Will be updated in updateChatDisplayNames()
                    lastMessage = "Hey there! How are you doing?",
                    timestamp = currentTime,
                    unreadCount = 0,
                    participantIds = mutableListOf(userId, "demo_user_1"),
                    type = "direct"
                )
            )

            // Add all demo chats to the stack
            chatManager.pushAll(demoChats)

            // Update display names
            updateChatDisplayNames()

            // Save demo chats to local storage
            saveChatsToLocalStorage()

            // Save to Firebase if enabled
            if (resources.getBoolean(R.bool.firebaseOn)) {
                saveChatsToFirebase()
            }

            // Update UI
            chatAdapter.notifyDataSetChanged()

            Log.d(TAG, "Demo chats added: ${chatManager.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding demo chats: ${e.message}")
            e.printStackTrace()
        }
    }

    // Update display names for direct chats
    private fun updateChatDisplayNames() {
        Log.d(TAG, "Updating chat display names")

        val allChats = chatManager.getAll()
        val updatedChats = mutableListOf<Chat>()

        for (chat in allChats) {
            // For direct chats, set displayName to the other user's name
            if (chat.type == "direct" && chat.participantIds.size == 2) {
                // Find the other user's ID (not current user)
                val otherUserId = chat.participantIds.find { it != userId }

                // If found, get their display name
                if (otherUserId != null) {
                    val otherUserName = userDisplayNames[otherUserId]

                    if (otherUserName != null) {
                        // Create a new chat with updated displayName
                        val updatedChat = chat.copy(displayName = otherUserName)
                        updatedChats.add(updatedChat)
                        continue
                    }
                }
            }

            // For group chats or if other user not found, keep original
            updatedChats.add(chat)
        }

        // Update chat manager with the updated chats
        chatManager.clear()
        chatManager.pushAll(updatedChats)

        // Save the updated chats with display names
        saveChatsToLocalStorage()
    }
    //endregion

    //region Local Storage
    private fun loadUsersFromLocalStorage() {
        Log.d(TAG, "Loading users from local storage")
        val jsonString = readUsersFromFile()

        if (jsonString.isEmpty()) {
            Log.d(TAG, "No users file found or empty file")
            return
        }

        try {
            val jsonObject = JSONObject(jsonString)
            val userIds = jsonObject.keys()

            // Clear existing map to avoid duplicates
            userDisplayNames.clear()

            // Populate the userDisplayNames map
            while (userIds.hasNext()) {
                val userId = userIds.next()
                val displayName = jsonObject.getString(userId)
                userDisplayNames[userId] = displayName
            }

            Log.d(TAG, "Loaded ${userDisplayNames.size} users from local storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading users from file: ${e.message}", e)
        }
    }

    private fun saveUsersToLocalStorage() {
        try {
            val jsonObject = JSONObject()

            for ((userId, displayName) in userDisplayNames) {
                jsonObject.put(userId, displayName)
            }

            val jsonString = jsonObject.toString()
            writeUsersToFile(jsonString)

            Log.d(TAG, "Saved ${userDisplayNames.size} users to local storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving users to file: ${e.message}")
        }
    }

    private fun readUsersFromFile(): String {
        val file = File(filesDir, USERS_FILE)
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    private fun writeUsersToFile(jsonString: String) {
        try {
            val file = File(filesDir, USERS_FILE)
            file.writeText(jsonString)
            Log.d(TAG, "Users saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing users to file: ${e.message}")
        }
    }

    private fun loadChatsFromLocalStorageWithoutSaving(): List<Chat> {
        Log.d(TAG, "Loading chats from local storage")
        val jsonString = readChatsFromFile()
        val localChats = mutableListOf<Chat>()

        if (jsonString.isEmpty()) {
            Log.d(TAG, "No chats file found or empty file")
            return localChats
        }

        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val chatObject = jsonArray.getJSONObject(i)
                val participantIdsJsonArray = chatObject.getJSONArray("participantIds")
                val participantIds = mutableListOf<String>()

                for (j in 0 until participantIdsJsonArray.length()) {
                    participantIds.add(participantIdsJsonArray.getString(j))
                }

                val chat = Chat(
                    id = chatObject.getString("id"),
                    name = chatObject.getString("name"),
                    displayName = chatObject.optString("displayName", chatObject.getString("name")),
                    lastMessage = chatObject.getString("lastMessage"),
                    timestamp = chatObject.getLong("timestamp"),
                    unreadCount = chatObject.getInt("unreadCount"),
                    participantIds = participantIds,
                    type = chatObject.getString("type")
                )

                // Only add chats where current user is a participant
                if (participantIds.contains(userId)) {
                    localChats.add(chat)
                }
            }

            Log.d(TAG, "Loaded ${localChats.size} chats from local storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chats from file: ${e.message}")
        }

        return localChats
    }

    private fun loadChatsFromLocalStorageAndDisplay() {
        val localChats = loadChatsFromLocalStorageWithoutSaving()
        if (localChats.isEmpty()) {
            addDemoChat()
        } else {
            chatManager.clear()
            chatManager.pushAll(localChats)
            updateChatDisplayNames()
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun readChatsFromFile(): String {
        val file = File(filesDir, CHATS_FILE)
        val content = if (file.exists()) {
            val text = file.readText()
            Log.d(TAG, "Chat file content length: ${text.length}")
            // Print first 100 chars to debug log
            if (text.isNotEmpty()) {
                Log.d(TAG, "Chat file content preview: ${text.take(100)}...")
            }
            text
        } else {
            Log.d(TAG, "Chat file doesn't exist at: ${file.absolutePath}")
            ""
        }
        return content
    }

    private fun saveChatsToLocalStorage() {
        val jsonArray = JSONArray()
        val allChats = chatManager.getAll()

        for (chat in allChats) {
            val chatObject = JSONObject().apply {
                put("id", chat.id)
                put("name", chat.name)
                put("displayName", chat.displayName)
                put("lastMessage", chat.lastMessage)
                put("timestamp", chat.timestamp)
                put("unreadCount", chat.unreadCount)

                // Create a JSONArray for participantIds
                val participantIdsArray = JSONArray()
                for (participantId in chat.participantIds) {
                    participantIdsArray.put(participantId)
                }
                put("participantIds", participantIdsArray)
                put("type", chat.type)
            }
            jsonArray.put(chatObject)
        }
        Log.d(TAG, "Saving ${chatManager.size()} chats to local storage")

        val jsonString = jsonArray.toString()
        writeChatsToFile(jsonString)
    }

    private fun writeChatsToFile(jsonString: String) {
        try {
            val file = File(filesDir, CHATS_FILE)
            file.writeText(jsonString)
            Log.d(TAG, "Chats saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file: ${e.message}")
        }
    }
    //endregion

    //region Event Handling
    override fun onResume() {
        super.onResume()
        // Save chats to Firebase when returning to this activity
        if (resources.getBoolean(R.bool.firebaseOn) && ::chatsReference.isInitialized) {
            saveChatsToFirebase()
        }
    }

    override fun onChatClick(chat: Chat) {
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            putExtra("CHAT_OBJECT", chat)
        }
        startActivity(intent)
        finish()
    }
    //endregion

    //region Firebase
    private fun setupFirebase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        chatsReference = firebaseDatabase.getReference("chats")
        usersReference = firebaseDatabase.getReference("users")

        // Add connection status listener
        val connectedRef = firebaseDatabase.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (!connected) {
                    Log.w(TAG, "Device is offline, using local data")
                    // When offline, load local data directly
                    if (userDisplayNames.isEmpty()) {
                        loadUsersFromLocalStorage()
                        if (userDisplayNames.isEmpty()) {
                            addDemoUsers()
                        }
                    }
                    loadChatsFromLocalStorageAndDisplay()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase connection listener cancelled")
                // When connection is cancelled, fall back to local data
                loadChatsFromLocalStorageAndDisplay()
            }
        })
    }

    private fun checkConnectionAndLoadUsersFromFirebase() {
        // Check if we're online before attempting Firebase operations
        val connectedRef = firebaseDatabase.getReference(".info/connected")
        connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    loadUsersFromFirebase()
                } else {
                    // If offline, proceed directly to loading chats
                    loadChats()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check connection status")
                // If checking connection fails, proceed with chats
                loadChats()
            }
        })
    }

    private fun checkConnectionAndLoadChatsFromFirebase(localChats: List<Chat>) {
        val connectedRef = firebaseDatabase.getReference(".info/connected")
        connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    loadChatsFromFirebaseAndMerge(localChats)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check connection status for chat loading")
            }
        })
    }

    private fun loadUsersFromFirebase() {
        Log.d(TAG, "Loading users from Firebase")

        // Add a timeout handler
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Firebase users loading timed out")
            if (userDisplayNames.isEmpty()) {
                addDemoUsers()
            }
            loadChats()
        }

        // Set a timeout (e.g., 5 seconds)
        timeoutHandler.postDelayed(timeoutRunnable, 5000)

        usersReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Cancel the timeout since we got a response
                timeoutHandler.removeCallbacks(timeoutRunnable)

                try {
                    // Rest of your code remains the same
                    // ...

                    // Save updated user data to local storage
                    saveUsersToLocalStorage()

                    // After loading users, proceed to load chats
                    loadChats()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in users onDataChange: ${e.message}")
                    e.printStackTrace()
                    loadChats()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Cancel the timeout since we got a response
                timeoutHandler.removeCallbacks(timeoutRunnable)

                Log.e(TAG, "Failed to load users from Firebase: ${error.message}")
                loadChats()
            }
        })
    }

    private fun loadChatsFromFirebaseAndMerge(localChats: List<Chat>) {
        Log.d(TAG, "Loading chats from Firebase")

        // Add a timeout handler
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Firebase chats loading timed out")
            runOnUiThread {
                chatManager.clear()
                chatManager.pushAll(localChats)
                updateChatDisplayNames()
                chatAdapter.notifyDataSetChanged()
            }
        }

        // Set a timeout (e.g., 5 seconds)
        timeoutHandler.postDelayed(timeoutRunnable, 5000)

        try {
            chatsReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Cancel the timeout since we got a response
                    timeoutHandler.removeCallbacks(timeoutRunnable)

                    // Rest of your code remains the same
                    // ...
                }

                override fun onCancelled(error: DatabaseError) {
                    // Cancel the timeout since we got a response
                    timeoutHandler.removeCallbacks(timeoutRunnable)

                    // Rest of your code remains the same
                    // ...
                }
            })
        } catch (e: Exception) {
            // Cancel the timeout since we got an exception
            timeoutHandler.removeCallbacks(timeoutRunnable)

            Log.e(TAG, "Exception during Firebase chat loading: ${e.message}")
            e.printStackTrace()

            // Fallback to local chats
            chatManager.clear()
            chatManager.pushAll(localChats)
            updateChatDisplayNames()
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun saveChatsToFirebase() {
        if (!resources.getBoolean(R.bool.firebaseOn)) {
            return
        }

        try {
            Log.d(TAG, "Saving chats to Firebase")
            val allChats = chatManager.getAll()

            // Only update the chats, don't delete anything
            for (chat in allChats) {
                try {
                    // Only save if user is a participant (this prevents deleting other people's chats)
                    if (chat.participantIds.contains(userId)) {
                        chatsReference.child(chat.id).setValue(chat)
                            .addOnSuccessListener {
                                Log.d(TAG, "Successfully saved chat ${chat.id} to Firebase")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to save chat ${chat.id}: ${e.message}")
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving chat ${chat.id} to Firebase: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.d(TAG, "Scheduled ${allChats.size} chats to be saved to Firebase")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during saveChatsToFirebase: ${e.message}")
            e.printStackTrace()
        }
    }
    //endregion
}
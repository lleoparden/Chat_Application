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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.UserSettings.userId
import com.facebook.shimmer.ShimmerFrameLayout  // Added import
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "MainActivity"
private const val CHATS_FILE = "chats.json"
private const val USERS_FILE = "users.json"

class MainActivity : AppCompatActivity(), ChatAdapter.OnChatClickListener, ChatAdapter.OnChatLongClickListener{

    // UI Components
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var searchButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newChatFab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var searchBar: EditText
    private lateinit var searchContainer: LinearLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private var isSearchVisible = false

    // Selection mode components
    private lateinit var normalToolbarView: View
    private lateinit var selectionToolbarView: View
    private lateinit var selectionCountTextView: TextView
    private lateinit var closeSelectionButton: ImageView
    private lateinit var deleteSelectedButton: ImageView
    private var isInSelectionMode = false
    private val selectedChatIds = mutableSetOf<String>()

    // Data Components
    private lateinit var chatAdapter: ChatAdapter
    private val chatManager = ChatManager()

    // Firebase Components
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var chatsReference: DatabaseReference
    private lateinit var usersReference: DatabaseReference
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.mainpage)

        initViews()
        setupUI()
        setupRecyclerView()
        setupFirebase()

        // Show shimmer effect before loading data
        showShimmerEffect()

        // Load data
        loadChats()
    }

    // Method to show shimmer effect
    private fun showShimmerEffect() {
        shimmerLayout.visibility = View.VISIBLE
        chatRecyclerView.visibility = View.GONE
        shimmerLayout.startShimmer()
    }

    // Method to hide shimmer effect
    private fun hideShimmerEffect() {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        chatRecyclerView.visibility = View.VISIBLE
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
        shimmerLayout = findViewById(R.id.shimmerLayout)  // Initialize shimmer layout

        // Initially hide search bar
        searchContainer.visibility = View.GONE

        // Initialize selection mode views
        normalToolbarView = findViewById(R.id.normalToolbarContent)
        selectionToolbarView = findViewById(R.id.selectionToolbarContent)
        selectionCountTextView = findViewById(R.id.selectionCountTextView)
        closeSelectionButton = findViewById(R.id.closeSelectionButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)

        // Initially hide search bar and selection toolbar
        searchContainer.visibility = View.GONE
        selectionToolbarView.visibility = View.GONE
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

        closeSelectionButton.setOnClickListener {
            exitSelectionMode()
        }

        deleteSelectedButton.setOnClickListener {
            deleteSelectedChats()
        }
    }

    private fun setupRecyclerView() {
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatManager.getAll(), this, this)
        chatRecyclerView.adapter = chatAdapter
    }

    // Update the toggleSearchBar method to handle shimmer
    private fun enterSelectionMode() {
        if (isInSelectionMode) return

        isInSelectionMode = true
        selectedChatIds.clear()

        // Update adapter FIRST
        chatAdapter.updateSelectionMode(true)

        // Then update UI
        normalToolbarView.visibility = View.GONE
        selectionToolbarView.visibility = View.VISIBLE
        newChatFab.visibility = View.GONE

        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        if (!isInSelectionMode) return

        isInSelectionMode = false
        selectedChatIds.clear()

        // Restore normal toolbar
        selectionToolbarView.visibility = View.GONE
        normalToolbarView.visibility = View.VISIBLE

        chatAdapter.updateSelectionMode(false)

        // Show FAB again
        newChatFab.visibility = View.VISIBLE

        // Update adapter to reset selection visual states
        chatAdapter.updateSelectionMode(false)
        chatAdapter.clearSelections() // Make sure to clear adapter's internal state too
    }

    private fun toggleChatSelection(chat: Chat) {
        if (selectedChatIds.contains(chat.id)) {
            selectedChatIds.remove(chat.id)
        } else {
            selectedChatIds.add(chat.id)
        }

        // Update adapter with the new selection state
        chatAdapter.updateSelectedItems(selectedChatIds)


        // If no chats are selected, exit selection mode
        if (selectedChatIds.isEmpty()) {
            exitSelectionMode()
            return
        }

        updateSelectionCount()
    }

    private fun updateSelectionCount() {
        val count = selectedChatIds.size
        selectionCountTextView.text = "$count selected"
    }


    private fun deleteSelectedChats() {
        var groupExists =false
        if (selectedChatIds.isEmpty()) return

        val groupChatsToLeave = selectedChatIds.toList().mapNotNull { chatId ->
            chatManager.getChatById(chatId)?.takeIf { it.type == "group" }
        }

        // Process group chats separately
        for (chat in groupChatsToLeave) {
            selectedChatIds.remove(chat.id) // Assuming chat has an 'id' property
            groupExists = true
        }

        // Remove selected chats from the chat manager
        val updatedChats = chatManager.getAll().filter { !selectedChatIds.contains(it.id) }

        // Remove from Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            for (chatId in selectedChatIds) {
                chatsReference.child(chatId).removeValue()
            }
        }

        // Update local list
        chatManager.clear()
        chatManager.pushAll(updatedChats)

        // Save to local storage
        saveChatsToLocalStorage()

        // Show confirmation
        if (selectedChatIds.size > 1) {
            Toast.makeText(this, "${selectedChatIds.size} chats deleted", Toast.LENGTH_SHORT).show()
        }else {
            Toast.makeText(this, "chat deleted", Toast.LENGTH_SHORT).show()
        }

        if(groupExists){
            Toast.makeText(this, "Can't delete groups", Toast.LENGTH_SHORT).show()
        }
        // Exit selection mode and update UI
        exitSelectionMode()
        chatAdapter.updateData(chatManager.getAll())
    }

    // Update the toggleSearchBar method to handle shimmer
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

            // Show shimmer effect when refreshing the list
            showShimmerEffect()

            // Reset RecyclerView to show all chats
            chatAdapter.updateData(chatManager.getAll())

            // Hide shimmer effect after data is loaded
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hideShimmerEffect()
            }, 500)
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            // Show all chats when query is empty
            chatAdapter.updateData(chatManager.getAll())
            return
        }

        // Show shimmer effect during search
        showShimmerEffect()

        // Use binary search to find matching chats
        val matchingChat = chatManager.findByName(query)

        // Use a handler to simulate search delay and show shimmer effect
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (matchingChat != null) {
                // Show only the exact match
                chatAdapter.updateData(listOf(matchingChat))
            } else {
                // If no exact match, try to find partial matches
                val partialMatches = chatManager.findPartialMatches(query)

                if (partialMatches.isNotEmpty()) {
                    chatAdapter.updateData(partialMatches)
                } else {
                    // Display a message for no results
                    Toast.makeText(this, "No chats found matching '$query'", Toast.LENGTH_SHORT).show()
                    // Show empty list
                    chatAdapter.updateData(emptyList())
                }
            }

            // Hide shimmer effect after search is complete
            hideShimmerEffect()
        }, 500) // 500ms delay to show the shimmer effect
    }
    //endregion

    //region Chat Management

    private fun loadChats() {
        showShimmerEffect()

        // Load local chats first
        val localChats = loadChatsFromLocalStorageWithoutSaving()

        // Update UI with local chats immediately
        chatManager.clear()
        chatManager.pushAll(localChats)
        chatAdapter.updateData(chatManager.getAll()) // Add this line to update adapter

        hideShimmerEffect() // Ensure this runs even if Firebase fails

        fetchUserDataAndUpdateDisplayNames()

        // Only attempt Firebase loading if explicitly enabled AND we're online
        if (resources.getBoolean(R.bool.firebaseOn)) {
            checkConnectionAndLoadChatsFromFirebase(localChats)
        }
    }

    private fun addDemoChat() {
        Log.d(TAG, "Adding demo chats")
        try {
            chatManager.clear()
            val currentTime = System.currentTimeMillis()

            // Include displayName directly in the Chat objects
            val demoChats = listOf(
                Chat(
                    id = "demo1",
                    name = "Demo Group", // Original name
                    displayName = "Demo Group", // Same as name for group chats
                    lastMessage = "Welcome to FireChat! This is a demo message.",
                    timestamp = currentTime - 6000,
                    unreadCount = 9,
                    participantIds = mutableListOf(userId, "demo_user_1", "demo_user_2"),
                    type = "group"
                ),
                Chat(
                    id = "demo2",
                    name = "demo_user_1", // Original ID/name
                    displayName = "John Doe", // Human-readable display name
                    lastMessage = "Hey there! How are you doing?",
                    timestamp = currentTime,
                    unreadCount = 0,
                    participantIds = mutableListOf(userId, "demo_user_1"),
                    type = "direct"
                )
            )

            // Add all demo chats to the stack
            chatManager.pushAll(demoChats)

            // Save demo chats to local storage
            saveChatsToLocalStorage()

            // Save to Firebase if enabled
            if (resources.getBoolean(R.bool.firebaseOn)) {
                saveChatsToFirebase()
            }

            // Update UI
            chatAdapter.updateData(chatManager.getAll())

            // Hide shimmer effect after data is loaded
            hideShimmerEffect()

            Log.d(TAG, "Demo chats added: ${chatManager.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding demo chats: ${e.message}")
            e.printStackTrace()

            // Hide shimmer effect even if there's an error
            hideShimmerEffect()
        }
    }

    // Update display names for direct chats
    private fun fetchUserDataAndUpdateDisplayNames() {
        if (!resources.getBoolean(R.bool.firebaseOn)) {
            return
        }

        // Create a map to store user IDs and their display names
        val userDisplayNames = mutableMapOf<String, String>()

        // Get all chats to find all participant IDs
        val allChats = chatManager.getAll()
        val allParticipantIds = mutableSetOf<String>()

        // Collect all unique participant IDs
        for (chat in allChats) {
            allParticipantIds.addAll(chat.participantIds)
        }

        // Remove the current user ID
        allParticipantIds.remove(UserSettings.userId)

        // If there are no other participants, we're done
        if (allParticipantIds.isEmpty()) {
            return
        }

        // Show shimmer effect while loading
        showShimmerEffect()

        // Keep track of how many users we've processed
        var processedCount = 0

        // Query Firestore for each user's data
        for (userId in allParticipantIds) {
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Get the user's display name from Firestore
                        val displayName = document.getString("displayName") ?: userId

                        // Add to our map
                        userDisplayNames[userId] = displayName

                        Log.d(TAG, "Fetched user $userId with display name: $displayName")
                    } else {
                        // If user document doesn't exist, use the ID as display name
                        userDisplayNames[userId] = userId
                        Log.d(TAG, "User document not found for $userId")
                    }

                    // Increment processed count
                    processedCount++

                    // If we've processed all users, update display names
                    if (processedCount == allParticipantIds.size) {
                        updateDisplayNamesAndRefresh(userDisplayNames)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching user $userId: ${e.message}")

                    // Use the ID as display name in case of failure
                    userDisplayNames[userId] = userId

                    // Increment processed count
                    processedCount++

                    // If we've processed all users, proceed even with some errors
                    if (processedCount == allParticipantIds.size) {
                        updateDisplayNamesAndRefresh(userDisplayNames)
                    }
                }
        }
    }

    // Helper method to update display names and refresh the UI
    private fun updateDisplayNamesAndRefresh(userDisplayNames: Map<String, String>) {
        // Update display names in chat manager
        chatManager.updateDisplayNames(userDisplayNames)

        // Update UI
        chatAdapter.updateData(chatManager.getAll())

        // Save changes
        saveChatsToLocalStorage()

        if (resources.getBoolean(R.bool.firebaseOn)) {
            saveChatsToFirebase()
        }

        // Hide shimmer effect
        hideShimmerEffect()
    }

    //endregion

    //region Local Storage

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

                // Get displayName if it exists, otherwise default to empty string
                val displayName = if (chatObject.has("displayName"))
                    chatObject.getString("displayName") else ""

                val chat = Chat(
                    id = chatObject.getString("id"),
                    name = chatObject.getString("name"),
                    displayName = displayName,
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
        // Show shimmer effect while loading
        showShimmerEffect()

        val localChats = loadChatsFromLocalStorageWithoutSaving()
        if (localChats.isEmpty()) {
            addDemoChat()
        } else {
            chatManager.clear()
            chatManager.pushAll(localChats)
            chatAdapter.updateData(chatManager.getAll())

            // Hide shimmer effect after data is loaded
            hideShimmerEffect()
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
        // First hide shimmer if it's visible
        if (shimmerLayout.visibility == View.VISIBLE) {
            hideShimmerEffect()
        }

        if (isInSelectionMode) {
            toggleChatSelection(chat)
        } else {
            val intent = Intent(this, ChatRoomActivity::class.java).apply {
                putExtra("CHAT_OBJECT", chat)
            }
            if (resources.getBoolean(R.bool.firebaseOn) && ::chatsReference.isInitialized) {
                saveChatsToFirebase()
            }
            startActivity(intent)
            finish()
        }
    }


    override fun onChatLongClick(chat: Chat): Boolean {
        if (!isInSelectionMode) {
            // Enter selection mode
            enterSelectionMode()
        }

        // Toggle selection after mode is updated
        toggleChatSelection(chat)
        return true
    }
    //endregion

    //region Firebase
    private fun setupFirebase() {
        try {
            firebaseDatabase = FirebaseDatabase.getInstance()
            chatsReference = firebaseDatabase.getReference("chats")
            usersReference = firebaseDatabase.getReference("users")
            firestore = Firebase.firestore

            // Add connection status listener with improved error handling
            val connectedRef = firebaseDatabase.getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (!connected) {
                        Log.w(TAG, "Device is offline, using local data")
                        loadChatsFromLocalStorageAndDisplay()
                        hideShimmerEffect() // Ensure shimmer is hidden when offline
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase connection listener cancelled: ${error.message}")
                    // When connection is cancelled, fall back to local data
                    loadChatsFromLocalStorageAndDisplay()
                    hideShimmerEffect() // Hide shimmer effect in case of error

                    // Show a user-friendly message
                    Toast.makeText(
                        this@MainActivity,
                        "Could not connect to the server. Using offline mode.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } catch (e : Exception) {
            Log.e(TAG, "Failed to initialize Firebase: ${e.message}")
            e.printStackTrace()

            // Fall back to local storage
            loadChatsFromLocalStorageAndDisplay()
            hideShimmerEffect()

            // Disable Firebase functionality
            val editor = getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit()
            editor.putBoolean("firebaseEnabled", false)
            editor.apply()
        }
    }



    private fun checkConnectionAndLoadChatsFromFirebase(localChats: List<Chat>) {
        try {
            val connectedRef = firebaseDatabase.getReference(".info/connected")
            connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            loadChatsFromFirebaseAndMerge(localChats)
                        } else {
                            // Explicitly handle the offline case
                            Log.d(TAG, "Device is offline, using local data only")
                            hideShimmerEffect()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing connection status: ${e.message}")
                        hideShimmerEffect()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to check connection status: ${error.message}")
                    hideShimmerEffect() // Ensure shimmer is hidden
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking Firebase connection: ${e.message}")
            hideShimmerEffect()
        }
    }

    private fun loadChatsFromFirebaseAndMerge(localChats: List<Chat>) {
        Log.d(TAG, "Loading chats from Firebase")

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
                                // Convert snapshot to Chat object
                                val chatId = chatSnapshot.key ?: continue
                                val name = chatSnapshot.child("name").getValue(String::class.java) ?: ""
                                val displayName = chatSnapshot.child("displayName").getValue(String::class.java) ?: ""
                                val lastMessage = chatSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
                                val timestamp = chatSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                                val unreadCount = chatSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0
                                val type = chatSnapshot.child("type").getValue(String::class.java) ?: "direct"

                                // Get participant IDs
                                val participantIds = mutableListOf<String>()
                                val participantsSnapshot = chatSnapshot.child("participantIds")
                                for (participantSnapshot in participantsSnapshot.children) {
                                    val participantId = participantSnapshot.getValue(String::class.java)
                                    if (participantId != null) {
                                        participantIds.add(participantId)
                                    }
                                }

                                // Create chat object
                                val chat = Chat(
                                    id = chatId,
                                    name = name,
                                    displayName = displayName,
                                    lastMessage = lastMessage,
                                    timestamp = timestamp,
                                    unreadCount = unreadCount,
                                    participantIds = participantIds,
                                    type = type
                                )

                                // Only process chats where the current user is a participant
                                if (participantIds.contains(userId)) {
                                    firebaseChats.add(chat)
                                    // Firebase data overrides local data for the same chat ID
                                    mergedChats[chat.id] = chat
                                    Log.d(TAG, "Added Firebase chat: ${chat.getEffectiveDisplayName()} (ID: ${chat.id})")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing chat from Firebase: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        Log.d(TAG, "Loaded ${firebaseChats.size} chats from Firebase")

                        // Update the chat manager with merged chats on the UI thread
                        runOnUiThread {
                            try {
                                // Update the chat manager with merged chats
                                chatManager.clear()
                                chatManager.pushAll(mergedChats.values.toList())

                                // Update display names if needed

                                chatAdapter.updateData(chatManager.getAll())

                                // Save the merged data back to local storage
                                saveChatsToLocalStorage()

                                Log.d(TAG, "Merged ${mergedChats.size} chats from local and Firebase")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating UI with merged chats: ${e.message}")
                                e.printStackTrace()

                                // Fallback to local chats
                                chatManager.clear()
                                chatManager.pushAll(localChats)
                                chatAdapter.updateData(chatManager.getAll())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onDataChange: ${e.message}")
                        e.printStackTrace()

                        // Fallback to local chats on the UI thread
                        runOnUiThread {
                            chatManager.clear()
                            chatManager.pushAll(localChats)
                            chatAdapter.updateData(chatManager.getAll())
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load chats from Firebase: ${error.message}")
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load chats from Firebase. Using local chats.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Just use the local chats if Firebase failed
                    runOnUiThread {
                        chatManager.clear()
                        chatManager.pushAll(localChats)
                        chatAdapter.updateData(chatManager.getAll())
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Firebase chat loading: ${e.message}")
            e.printStackTrace()

            // Fallback to local chats
            chatManager.clear()
            chatManager.pushAll(localChats)
            chatAdapter.updateData(chatManager.getAll())
        }
    }

    private fun saveChatsToFirebase() {
        if (!resources.getBoolean(R.bool.firebaseOn) ) {
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
}



//endregion

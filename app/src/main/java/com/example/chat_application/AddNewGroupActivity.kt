package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.adapters.SelectedMemberAdapter
import com.example.chat_application.adapters.UserAdapter
import com.example.chat_application.dataclasses.Chat
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AddNewGroupActivity : AppCompatActivity(), UserAdapter.OnUserClickListener {
    private val TAG = "AddNewGroupActivity"

    // UI Components
    private lateinit var searchEditText: TextInputEditText
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var selectedMembersRecyclerView: RecyclerView
    private lateinit var emptyResultsTextView: androidx.appcompat.widget.AppCompatTextView
    private lateinit var progressIndicator: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var createGroupButton: Button
    private lateinit var groupNameEditText: EditText

    // Data Components
    private lateinit var userAdapter: UserAdapter
    private lateinit var selectedMemberAdapter: SelectedMemberAdapter
    private val usersList = mutableListOf<UserData>()
    private val localUsersList = mutableListOf<UserData>()
    private val selectedUsers = mutableListOf<UserData>()

    // Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase
    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting activity")
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_new_group)

        // Initialize Firebase if enabled
        if (firebaseEnabled) {
            Log.d(TAG, "onCreate: Initializing Firebase components")
            firestore = FirebaseFirestore.getInstance()
            realtimeDb = FirebaseDatabase.getInstance()
        }

        initViews()
        setupUI()
        loadLocalUsers()
        showEmptyState("Search for users to add to your group")
        Log.i(TAG, "onCreate: Activity setup complete")
    }

    private fun initViews() {
        Log.d(TAG, "initViews: Finding view references")
        try {
            searchEditText = findViewById(R.id.searchEditText)
            usersRecyclerView = findViewById(R.id.usersRecyclerView)
            selectedMembersRecyclerView = findViewById(R.id.selectedMembersRecyclerView)
            emptyResultsTextView = findViewById(R.id.emptyResultsTextView)
            progressIndicator = findViewById(R.id.progressIndicator)
            createGroupButton = findViewById(R.id.createGroupButton)
            groupNameEditText = findViewById(R.id.groupNameEditText)

            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            toolbar.setNavigationOnClickListener {
                Log.d(TAG, "Back navigation clicked")
                navigateToMainActivity()
            }
            Log.d(TAG, "initViews: All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initViews: Error initializing views", e)
        }
    }

    private fun setupUI() {
        Log.d(TAG, "setupUI: Configuring UI components")
        try {
            // Setup RecyclerView for search results
            usersRecyclerView.layoutManager = LinearLayoutManager(this)
            // Use empty set for existingChatUsers since we allow adding any user to groups
            userAdapter = UserAdapter(usersList, emptySet(), this)
            usersRecyclerView.adapter = userAdapter

            // Setup RecyclerView for selected members
            selectedMembersRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            selectedMemberAdapter = SelectedMemberAdapter(selectedUsers) { user ->
                removeSelectedUser(user)
            }
            selectedMembersRecyclerView.adapter = selectedMemberAdapter

            // Initially hide the selected members RecyclerView
            if (selectedUsers.isEmpty()) {
                selectedMembersRecyclerView.visibility = View.GONE
            }

            groupNameEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateCreateButtonState()
                }
            })

            // Setup search functionality
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    Log.d(TAG, "Search query changed: '$query'")

                    if (query.isEmpty()) {
                        // Clear results if search is empty
                        Log.d(TAG, "Query empty, clearing results")
                        usersList.clear()
                        userAdapter.notifyDataSetChanged()
                        showEmptyState("Search for users to add to your group")
                    } else if (firebaseEnabled && isFullPhoneNumber(query)) {
                        // Search using Firebase for full phone numbers
                        Log.d(TAG, "Full phone number detected, searching Firebase")
                        searchUsersByPhoneOnFirebase(query)
                    } else {
                        // Search local users for any query
                        Log.d(TAG, "Searching local users")
                        searchLocalUsersByPhone(query)
                    }
                }
            })

            // Setup create group button
            createGroupButton.setOnClickListener {
                Log.d(TAG, "Create group button clicked")
                createGroup()
            }

            Log.i(TAG, "setupUI: All UI components configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "setupUI: Error configuring UI components", e)
        }
    }


    private fun isFullPhoneNumber(query: String): Boolean {
        // Check if the number consists of exactly 11 digits
        return query.all { it.isDigit() } && query.length == 11 ||
                // Or if it starts with "+" followed by exactly 10 digits
                (query.startsWith("+") && query.drop(1).all { it.isDigit() } && query.length == 11)
    }

    private fun loadLocalUsers() {
        Log.d(TAG, "loadLocalUsers: Loading users from local JSON file")
        try {
            progressIndicator.visibility = View.VISIBLE

            // Load from internal files directory instead of assets
            val file = File(filesDir, "local_user.json")

            if (!file.exists()) {
                Log.e(TAG, "loadLocalUsers: local_user.json not found in files directory: ${file.absolutePath}")
                // Create default users if the file doesn't exist
                progressIndicator.visibility = View.GONE
                return
            }

            Log.d(TAG, "loadLocalUsers: Reading file from: ${file.absolutePath}")

            // Read from local_user.json in internal files directory
            val jsonString = file.readText()

            val jsonArray = JSONArray(jsonString)

            // Parse JSON and create UserData objects
            for (i in 0 until jsonArray.length()) {
                val userObject = jsonArray.getJSONObject(i)

                val userData = UserData(
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

                localUsersList.add(userData)
            }

            Log.d(TAG, "loadLocalUsers: Loaded ${localUsersList.size} users from local file")

        } catch (e: Exception) {
            Log.e(TAG, "loadLocalUsers: Error loading local users", e)
        } finally {
            progressIndicator.visibility = View.GONE
        }
    }

    private fun searchLocalUsersByPhone(phoneQuery: String) {
        Log.i(TAG, "searchLocalUsersByPhone: Searching local users with phone: '$phoneQuery'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE
            emptyResultsTextView.visibility = View.GONE

            // Clear any existing users
            usersList.clear()

            // Get current user ID
            val currentUserId = UserSettings.userId
            if (currentUserId == null) {
                Log.e(TAG, "searchLocalUsersByPhone: Current user ID is null")
                progressIndicator.visibility = View.GONE
                return
            }

            // Search for users whose phone number contains the query
            val results = localUsersList.filter {
                it.uid != currentUserId && // Exclude current user
                        it.phoneNumber.contains(phoneQuery) // Phone contains query
            }

            Log.d(TAG, "searchLocalUsersByPhone: Found ${results.size} matching users")

            if (results.isNotEmpty()) {
                usersList.addAll(results)
                userAdapter.notifyDataSetChanged()
                emptyResultsTextView.visibility = View.GONE
            } else {
                showEmptyState("No users found with this phone number")
            }

            progressIndicator.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "searchLocalUsersByPhone: Error searching local users", e)
            progressIndicator.visibility = View.GONE
            showEmptyState("An error occurred while searching")
        }
    }

    private fun searchUsersByPhoneOnFirebase(phoneQuery: String) {
        Log.i(TAG, "searchUsersByPhoneOnFirebase: Searching for users with phone: '$phoneQuery'")
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE
            emptyResultsTextView.visibility = View.GONE

            // Clear any existing users in search results
            usersList.clear()

            // Get current user ID
            val currentUserId = UserSettings.userId
            if (currentUserId == null) {
                Log.e(TAG, "searchUsersByPhoneOnFirebase: Current user ID is null")
                return
            }
            Log.d(TAG, "searchUsersByPhoneOnFirebase: Current user ID: $currentUserId")

            // Try multiple phone number formats to increase chances of finding a match
            val phoneQueries = listOf(
                phoneQuery,                     // Original input
                "+$phoneQuery",                 // With + prefix
                "+2$phoneQuery",                // With country code +2
                "+20$phoneQuery",
                phoneQuery.replace("+", "")     // Without + if it exists
            )

            var queriesCompleted = 0
            var totalUsersFound = 0

            for (formattedQuery in phoneQueries) {
                firestore.collection("users")
                    .whereGreaterThanOrEqualTo("phoneNumber", formattedQuery)
                    .whereLessThanOrEqualTo("phoneNumber", formattedQuery + "\uf8ff")
                    .limit(10)
                    .get()
                    .addOnSuccessListener { documents ->
                        queriesCompleted++
                        Log.d(TAG, "Query for '$formattedQuery' found ${documents.size()} documents")

                        if (!documents.isEmpty) {
                            for (document in documents) {
                                try {
                                    // Create UserData object
                                    val userData = UserData(
                                        uid = document.getString("uid") ?: document.id,
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
                                        lastSeen = document.getLong("lastSeen")?.toString() ?: "",
                                        profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                                    )

                                    // Add users that are not the current user, not already in the list,
                                    // and not already selected
                                    if (userData.uid != currentUserId &&
                                        !usersList.any { it.uid == userData.uid } &&
                                        !selectedUsers.any { it.uid == userData.uid }) {
                                        usersList.add(userData)
                                        totalUsersFound++
                                        Log.d(TAG, "Added user: ${userData.displayName}")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing user document: ${document.id}", e)
                                }
                            }

                            // Update adapter with newly found users
                            userAdapter.notifyDataSetChanged()
                        }

                        // Check if all queries are complete
                        if (queriesCompleted == phoneQueries.size) {
                            progressIndicator.visibility = View.GONE

                            if (totalUsersFound == 0) {
                                showEmptyState("No users found with this phone number")
                            } else {
                                emptyResultsTextView.visibility = View.GONE
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        queriesCompleted++
                        Log.e(TAG, "Error searching for '$formattedQuery'", exception)

                        // Check if all queries are complete
                        if (queriesCompleted == phoneQueries.size) {
                            progressIndicator.visibility = View.GONE

                            if (totalUsersFound == 0) {
                                showEmptyState("No users found with this phone number")
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchUsersByPhoneOnFirebase: Unexpected error", e)
            progressIndicator.visibility = View.GONE
            showEmptyState("An unexpected error occurred")
        }
    }

    private fun showEmptyState(message: String) {
        Log.d(TAG, "showEmptyState: Showing empty state message: '$message'")
        emptyResultsTextView.text = message
        emptyResultsTextView.visibility = View.VISIBLE
    }

    override fun onUserClick(user: UserData) {
        Log.i(TAG, "onUserClick: User clicked: ${user.displayName} (${user.uid})")
        try {
            // Add user to selected users list
            if (!selectedUsers.any { it.uid == user.uid }) {
                selectedUsers.add(user)
                selectedMemberAdapter.notifyItemInserted(selectedUsers.size - 1)

                // Show selected members RecyclerView if this is the first member
                if (selectedUsers.size == 1) {
                    selectedMembersRecyclerView.visibility = View.VISIBLE
                }

                // Remove user from search results
                val position = usersList.indexOfFirst { it.uid == user.uid }
                if (position != -1) {
                    usersList.removeAt(position)
                    userAdapter.notifyItemRemoved(position)
                }

                // Enable create group button if we have at least one member
                updateCreateButtonState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onUserClick: Error adding user to selection", e)
            Toast.makeText(this, "Error adding user to group", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSelectedUser(user: UserData) {
        Log.i(TAG, "removeSelectedUser: Removing user: ${user.displayName}")
        try {
            val position = selectedUsers.indexOfFirst { it.uid == user.uid }
            if (position != -1) {
                selectedUsers.removeAt(position)
                selectedMemberAdapter.notifyItemRemoved(position)

                // Hide selected members RecyclerView if no members left
                if (selectedUsers.isEmpty()) {
                    selectedMembersRecyclerView.visibility = View.GONE
                }

                // Add user back to search results if they match current search
                val searchQuery = searchEditText.text.toString().trim()
                if (searchQuery.isNotEmpty() && user.phoneNumber.contains(searchQuery)) {
                    usersList.add(user)
                    userAdapter.notifyItemInserted(usersList.size - 1)
                }

                // Update create button state
                updateCreateButtonState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeSelectedUser: Error removing user", e)
        }
    }

    private fun updateCreateButtonState() {
        createGroupButton.isEnabled = selectedUsers.isNotEmpty() &&
                groupNameEditText.text.toString().trim().isNotEmpty()
    }

    private fun createGroup() {
        Log.i(TAG, "createGroup: Creating new group chat")
        try {
            val groupName = groupNameEditText.text.toString().trim()

            if (groupName.isEmpty()) {
                Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show()
                return
            }

            if (selectedUsers.isEmpty()) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show()
                return
            }

            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Generate a unique ID for the group
            val groupId = UUID.randomUUID().toString()

            // Create participant IDs map (including current user)
            val participantIds = HashMap<String, Boolean>()
            val currentUserId = UserSettings.userId ?: ""
            participantIds[currentUserId] = true // Add current user
            for (user in selectedUsers) {
                participantIds[user.uid] = true
            }

            // Create unread count map for each participant, initialized to 0
            val unreadCount = mutableMapOf<String, Int>()
            for (participantId in participantIds.keys) {
                unreadCount[participantId] = 0
            }

            // Create Chat object
            val groupChat = Chat(
                id = groupId,
                name = groupName, // Use group name as chat name
                displayName = groupName,
                lastMessage = "Group created",
                timestamp = System.currentTimeMillis(),
                unreadCount = unreadCount,
                participantIds = participantIds,
                type = "group" // Mark as a group chat
            )


            if (firebaseEnabled) {
                saveGroupToRealtimeDb(groupChat)
            } else {
                saveGroupToLocalStorage(groupChat)
            }

        } catch (e: Exception) {
            Log.e(TAG, "createGroup: Error creating group", e)
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "Error creating group", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGroupToRealtimeDb(groupChat: Chat) {
        Log.d(TAG, "saveGroupToRealtimeDb: Saving group to Firebase Realtime Database")

        // Create a map for the chat data
        val chatData = mapOf(
            "id" to groupChat.id,
            "name" to groupChat.name,
            "displayName" to groupChat.displayName,
            "lastMessage" to groupChat.lastMessage,
            "timestamp" to groupChat.timestamp,
            "unreadCount" to groupChat.unreadCount,
            "participantIds" to groupChat.participantIds,
            "type" to groupChat.type
        )

        // Save basic chat info to the 'chats' node
        realtimeDb.reference.child("chats").child(groupChat.id).setValue(chatData)
            .addOnSuccessListener {
                Log.d(TAG, "Group chat created in Realtime DB successfully")
                progressIndicator.visibility = View.GONE
                navigateToChatRoom(groupChat)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating group in Realtime DB", e)
                progressIndicator.visibility = View.GONE
                Toast.makeText(this, "Error creating group", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveGroupToLocalStorage(groupChat: Chat) {
        Log.d(TAG, "saveGroupToLocalStorage: Saving group to local storage")
        try {
            // Save to chats.json
            val chatsFile = File(filesDir, "chats.json")
            var jsonArray = JSONArray()

            if (chatsFile.exists()) {
                val fileContent = chatsFile.readText()
                if (fileContent.isNotEmpty()) {
                    jsonArray = JSONArray(fileContent)
                }
            }

            // Create JSON object for the new group chat
            val groupJson = JSONObject().apply {
                put("id", groupChat.id)
                put("name", groupChat.name)
                put("displayName", groupChat.displayName)
                put("lastMessage", groupChat.lastMessage)
                put("timestamp", groupChat.timestamp)
                put("unreadCount", groupChat.unreadCount)
                put("type", groupChat.type)

                // Create participantIds as a JSONObject with boolean values
                val participantsJson = JSONObject()
                for ((id, value) in groupChat.participantIds) {
                    participantsJson.put(id, value)
                }
                put("participantIds", participantsJson)
            }

            // Add to chats array
            jsonArray.put(groupJson)

            // Write back to file
            chatsFile.writeText(jsonArray.toString())

            Log.d(TAG, "Group saved to local storage successfully")
            progressIndicator.visibility = View.GONE
            navigateToChatRoom(groupChat)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving group to local storage", e)
            progressIndicator.visibility = View.GONE
            Toast.makeText(this, "Error creating group", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToChatRoom(chat: Chat) {
        Log.i(TAG, "navigateToChatRoom: Navigating to chat room for group: ${chat.name}")
        try {
            val intent = Intent(this, ChatRoomActivity::class.java).apply {
                putExtra("CHAT_OBJECT", chat)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "navigateToChatRoom: Error navigating to chat room", e)
            navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        Log.i(TAG, "navigateToMainActivity: Navigating back to main activity")
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        } catch (e: Exception) {
            Log.e(TAG, "navigateToMainActivity: Error navigating to main activity", e)
        }
    }
}